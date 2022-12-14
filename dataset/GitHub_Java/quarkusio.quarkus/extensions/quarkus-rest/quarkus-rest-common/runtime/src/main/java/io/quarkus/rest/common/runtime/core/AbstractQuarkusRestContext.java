package io.quarkus.rest.common.runtime.core;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.ConnectionCallback;

import org.jboss.logging.Logger;

import io.quarkus.arc.InjectableContext;
import io.quarkus.arc.ManagedContext;

public abstract class AbstractQuarkusRestContext<T extends AbstractQuarkusRestContext<T, H>, H extends RestHandler<T>>
        implements Runnable, Closeable, QuarkusRestContext {
    protected static final Logger log = Logger.getLogger(AbstractQuarkusRestContext.class);
    protected H[] handlers;
    protected H[] abortHandlerChain;
    protected int position;
    protected Throwable throwable;
    private boolean suspended = false;
    private volatile boolean requestScopeActivated = false;
    private volatile boolean running = false;
    private volatile Executor executor;
    private Map<String, Object> properties;
    private final ManagedContext requestContext;
    private InjectableContext.ContextState currentRequestScope;
    private List<CompletionCallback> completionCallbacks;
    private List<ConnectionCallback> connectionCallbacks;

    public AbstractQuarkusRestContext(H[] handlerChain, H[] abortHandlerChain, ManagedContext requestContext) {
        this.handlers = handlerChain;
        this.abortHandlerChain = abortHandlerChain;
        this.requestContext = requestContext;
    }

    public void suspend() {
        suspended = true;
    }

    public void resume() {
        resume((Executor) null);
    }

    public synchronized void resume(Throwable throwable) {
        handleException(throwable);
        resume((Executor) null);
    }

    public synchronized void resume(Executor executor) {
        if (running) {
            this.executor = executor;
            if (executor == null) {
                suspended = false;
            }
        } else {
            suspended = false;
            if (executor == null) {
                getEventLoop().execute(this);
            } else {
                executor.execute(this);
            }
        }
    }

    public H[] getAbortHandlerChain() {
        return abortHandlerChain;
    }

    public T setAbortHandlerChain(H[] abortHandlerChain) {
        this.abortHandlerChain = abortHandlerChain;
        return (T) this;
    }

    public void close() {
        //TODO: do we even have any other resources to close?
        if (currentRequestScope != null) {
            requestContext.destroy(currentRequestScope);
        }
        onComplete(throwable);
    }

    public Throwable getThrowable() {
        return throwable;
    }

    protected abstract Executor getEventLoop();

    @Override
    public void run() {
        running = true;
        boolean submittedToExecutor = false;
        //if this is a blocking target we don't activate for the initial non-blocking part
        //unless there are pre-mapping filters as these may require CDI
        boolean disasociateRequestScope = false;
        try {
            while (position < handlers.length) {
                int pos = position;
                position++; //increment before, as reset may reset it to zero
                try {
                    handlers[pos].handle((T) this);
                    if (suspended) {
                        Executor exec = null;
                        synchronized (this) {
                            if (requestScopeActivated) {
                                if (position != handlers.length) {
                                    currentRequestScope = requestContext.getState();
                                    disasociateRequestScope = true;
                                }
                                requestScopeActivated = false;
                            }
                            if (this.executor != null) {
                                //resume happened in the meantime
                                suspended = false;
                                exec = this.executor;
                            } else if (suspended) {
                                running = false;
                                return;
                            }
                        }
                        if (exec != null) {
                            //outside sync block
                            exec.execute(this);
                            submittedToExecutor = true;
                            return;
                        }
                    }
                } catch (Throwable t) {
                    boolean over = handlers == abortHandlerChain;
                    handleException(t);
                    if (over) {
                        running = false;
                        return;
                    }
                }
            }
            running = false;
        } catch (Throwable t) {
            handleUnrecoverableError(t);
            running = false;
        } finally {
            // we need to make sure we don't close the underlying stream in the event loop if the task
            // has been offloaded to the executor
            if (position == handlers.length && !suspended && !submittedToExecutor) {
                close();
            } else if (disasociateRequestScope) {
                requestContext.deactivate();
            }
        }
    }

    public void requireCDIRequestScope() {
        if (!running) {
            throw new RuntimeException("Cannot be called when outside a handler chain");
        }
        if (requestScopeActivated) {
            return;
        }
        requestScopeActivated = true;
        if (currentRequestScope == null) {
            requestContext.activate();
            currentRequestScope = requestContext.getState();
            handleRequestScopeActivation();
        } else {
            requestContext.activate(currentRequestScope);
        }
    }

    protected abstract void handleRequestScopeActivation();

    /**
     * Restarts handler chain processing on a chain that does not target a specific resource
     * <p>
     * Generally used to abort processing.
     *
     * @param newHandlerChain The new handler chain
     */
    public void restart(H[] newHandlerChain) {
        restart(newHandlerChain, false);
    }

    public void restart(H[] newHandlerChain, boolean keepTarget) {
        this.handlers = newHandlerChain;
        position = 0;
        restarted(keepTarget);
    }

    protected abstract void restarted(boolean keepTarget);

    public boolean isSuspended() {
        return suspended;
    }

    public T setSuspended(boolean suspended) {
        this.suspended = suspended;
        return (T) this;
    }

    public boolean isRunning() {
        return running;
    }

    public T setRunning(boolean running) {
        this.running = running;
        return (T) this;
    }

    public Executor getExecutor() {
        return executor;
    }

    public T setExecutor(Executor executor) {
        this.executor = executor;
        return (T) this;
    }

    public int getPosition() {
        return position;
    }

    public T setPosition(int position) {
        this.position = position;
        return (T) this;
    }

    public H[] getHandlers() {
        return handlers;
    }

    /**
     * If we are on the abort chain already, send a 500. If not, turn the throwable into
     * a response result and switch to the abort chain
     */
    public void handleException(Throwable t) {
        if (handlers == abortHandlerChain) {
            handleUnrecoverableError(t);
        } else {
            this.throwable = t;
            restart(abortHandlerChain);
        }
    }

    public void handleException(Throwable t, boolean keepSameTarget) {
        if (handlers == abortHandlerChain) {
            handleUnrecoverableError(t);
        } else {
            this.throwable = t;
            restart(abortHandlerChain, keepSameTarget);
        }
    }

    protected abstract void handleUnrecoverableError(Throwable throwable);

    public Object getProperty(String name) {
        if (properties == null) {
            return null;
        }
        return properties.get(name);
    }

    public Collection<String> getPropertyNames() {
        if (properties == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableSet(properties.keySet());
    }

    public void setProperty(String name, Object object) {
        if (object == null) {
            removeProperty(name);
            return;
        }
        if (properties == null) {
            properties = new HashMap<>();
        }
        properties.put(name, object);
    }

    public void removeProperty(String name) {
        if (properties == null) {
            return;
        }
        properties.remove(name);
    }

    synchronized void onComplete(Throwable throwable) {
        if (completionCallbacks != null) {
            for (CompletionCallback callback : completionCallbacks) {
                callback.onComplete(throwable);
            }
        }
    }

    @Override
    public synchronized void registerCompletionCallback(CompletionCallback callback) {
        if (completionCallbacks == null)
            completionCallbacks = new ArrayList<>();
        completionCallbacks.add(callback);
    }

    @Override
    public synchronized void registerConnectionCallback(ConnectionCallback callback) {
        if (connectionCallbacks == null)
            connectionCallbacks = new ArrayList<>();
        connectionCallbacks.add(callback);
    }
}
