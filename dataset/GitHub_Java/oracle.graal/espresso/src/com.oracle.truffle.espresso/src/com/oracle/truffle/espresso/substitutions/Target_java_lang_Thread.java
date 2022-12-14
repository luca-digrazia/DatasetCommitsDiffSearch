/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.truffle.espresso.substitutions;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoExitException;
import com.oracle.truffle.espresso.runtime.StaticObject;

// @formatter:off
/**
 * Thread state manipulation:
 *
 * public static State toThreadState(int var0) {
 *         if ((var0 & 4) != 0) {
 *             return State.RUNNABLE;
 *         } else if ((var0 & 1024) != 0) {
 *             return State.BLOCKED;
 *         } else if ((var0 & 16) != 0) {
 *             return State.WAITING;
 *         } else if ((var0 & 32) != 0) {
 *             return State.TIMED_WAITING;
 *         } else if ((var0 & 2) != 0) {
 *             return State.TERMINATED;
 *         } else {
 *             return (var0 & 1) == 0 ? State.NEW : State.RUNNABLE;
 *         }
 *     }
 */
// @formatter:on
@EspressoSubstitutions
public final class Target_java_lang_Thread {
    private static final java.lang.reflect.Method isInterrupted;
    static {
        try {
            isInterrupted = Thread.class.getDeclaredMethod("isInterrupted", boolean.class);
            isInterrupted.setAccessible(true);
        } catch (Throwable e) {
            throw EspressoError.shouldNotReachHere();
        }
    }

    public static void incrementThreadCounter(StaticObject thread, Field hiddenField) {
        assert hiddenField.isHidden();
        Long counter = (Long) thread.getHiddenField(hiddenField);
        if (counter == null) {
            counter = 0L;
        }
        ++counter;
        thread.setHiddenField(hiddenField, counter);
    }

    public static long getThreadCounter(StaticObject thread, Field hiddenField) {
        assert hiddenField.isHidden();
        Long counter = (Long) thread.getHiddenField(hiddenField);
        if (counter == null) {
            counter = 0L;
        }
        return counter;
    }

    public enum State {
        NEW(0),
        RUNNABLE(4),
        BLOCKED(1024),
        WAITING(16),
        TIMED_WAITING(32),
        TERMINATED(2);

        public final int value;

        State(int value) {
            this.value = value;
        }

    }

    public static void fromRunnable(StaticObject self, Meta meta, State state) {
        assert self.getIntField(meta.java_lang_Thread_threadStatus) == State.RUNNABLE.value;
        setState(self, meta, state);
        checkDeprecatedState(meta, self);
    }

    public static void toRunnable(StaticObject self, Meta meta, State state) {
        assert state == State.RUNNABLE;
        try {
            checkDeprecatedState(meta, self);
        } finally {
            setState(self, meta, state);
        }
    }

    private static void setState(StaticObject self, Meta meta, State state) {
        self.setIntField(meta.java_lang_Thread_threadStatus, state.value);
    }

    @TruffleBoundary
    public static void checkDeprecatedState(Meta meta, StaticObject thread) {
        EspressoContext context = meta.getContext();
        assert thread == context.getCurrentThread();
        if (context.shouldCheckStop()) {
            KillStatus status = getKillStatus(thread);
            switch (status) {
                case NORMAL:
                case EXITING:
                case KILLED:
                    break;
                case KILL:
                    if (context.isClosing()) {
                        // Give some leeway during closing.
                        setThreadStop(thread, KillStatus.KILLED);
                    } else {
                        setThreadStop(thread, KillStatus.NORMAL);
                    }
                    // check if death cause throwable is set, if not throw ThreadDeath
                    StaticObject deathThrowable = (StaticObject) getDeathThrowable(thread);
                    throw deathThrowable != null ? Meta.throwException(deathThrowable) : Meta.throwException(meta.java_lang_ThreadDeath);
                case SHUTDOWN:
                    // This thread refuses to stop. Send a host exception.
                    // throw getMeta().throwEx(ThreadDeath.class);
                    assert context.isClosing();
                    throw new EspressoExitException(context.getExitStatus());
            }
        }
        if (context.shouldCheckSuspend()) {
            trySuspend(thread);
        }
    }

    @Substitution
    public static @Host(Thread.class) StaticObject currentThread(@InjectMeta Meta meta) {
        return meta.getContext().getCurrentThread();
    }

    @Substitution
    public static @Host(Thread[].class) StaticObject getThreads(@InjectMeta Meta meta) {
        return StaticObject.createArray(meta.java_lang_Thread.array(), meta.getContext().getActiveThreads());
    }

    @Substitution
    public static @Host(StackTraceElement[][].class) StaticObject dumpThreads(@Host(Thread[].class) StaticObject threads, @InjectMeta Meta meta) {
        if (StaticObject.isNull(threads)) {
            throw meta.throwNullPointerException();
        }
        if (threads.length() == 0) {
            throw Meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        StaticObject trace = StaticObject.createArray(meta.java_lang_StackTraceElement.array(), StaticObject.EMPTY_ARRAY);
        StaticObject[] toWrap = new StaticObject[threads.length()];
        Arrays.fill(toWrap, trace);
        return StaticObject.createArray(meta.java_lang_StackTraceElement.array().array(), toWrap);
    }

    @TruffleBoundary
    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    public static void start0(@Host(Thread.class) StaticObject self,
                    // Checkstyle: stop
                    @GuestCall(target = "java_lang_Thread_exit") DirectCallNode threadExit,
                    // Checkstyle: resume
                    @InjectMeta Meta meta) {
        EspressoContext context = meta.getContext();
        if (context.multiThreadingEnabled()) {
            // Thread.start() is synchronized.
            KillStatus killStatus = getKillStatus(self);
            if (killStatus != null || context.isClosing()) {

                self.getLock().lock();
                try {
                    self.setIntField(meta.java_lang_Thread_threadStatus, State.TERMINATED.value);
                    // Notify waiting threads you were terminated
                    self.getLock().signalAll();
                } finally {
                    self.getLock().unlock();
                }

                return;
            }
            setThreadStop(self, KillStatus.NORMAL);
            if (getSuspendLock(self) == null) {
                initSuspendLock(self);
            }
            Thread hostThread = context.getEnv().createThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        context.getVM().attachThread(Thread.currentThread());
                        try {
                            if (meta.getContext().IsolatedNamespace) {
                                // Initialize TLS related to __ctype_b_loc to avoid crashes on
                                // glibc.
                                meta.getContext().getJNI().ctypeInit();
                            }
                            // Execute the payload
                            self.getKlass().vtableLookup(meta.java_lang_Thread_run.getVTableIndex()).invokeDirect(self);
                            checkDeprecatedState(meta, self);
                        } catch (EspressoException uncaught) {
                            meta.java_lang_Thread_dispatchUncaughtException.invokeDirect(self, uncaught.getExceptionObject());
                        }
                    } catch (EspressoExitException exit) {
                        /* Suppress */
                    } finally {
                        terminate(self, threadExit, meta);
                        if (context.isClosing()) {
                            // Ignore exceptions that arise during closing.
                            return;
                        }
                    }
                }
            });

            self.setHiddenField(meta.HIDDEN_HOST_THREAD, hostThread);
            hostThread.setDaemon(self.getBooleanField(meta.java_lang_Thread_daemon));
            self.setIntField(meta.java_lang_Thread_threadStatus, State.RUNNABLE.value);
            hostThread.setPriority(self.getIntField(meta.java_lang_Thread_priority));
            if (isInterrupted(self, false)) {
                hostThread.interrupt();
            }
            context.registerThread(hostThread, self);
            context.getLogger().fine(() -> {
                String guestName = Target_java_lang_Thread.getThreadName(meta, self);
                long guestId = Target_java_lang_Thread.getThreadId(meta, self);
                return String.format("Thread.start0: [HOST:%s, %d], [GUEST:%s, %d]", hostThread.getName(), hostThread.getId(), guestName, guestId);
            });
            hostThread.start();
        } else {
            String reason = context.getMultiThreadingDisabledReason();
            Klass threadKlass = self.getKlass();
            if (threadKlass == meta.java_lang_ref_Finalizer$FinalizerThread || threadKlass == meta.java_lang_ref_Reference$ReferenceHandler) {
                // no exception: bootstrap code cannot recover from this
                EspressoLanguage.getCurrentContext().getLogger().warning(() -> {
                    String guestName = Target_java_lang_Thread.getThreadName(meta, self);
                    String className = threadKlass.getExternalName();
                    return "Thread.start() called on " + className + " / " + guestName + " but thread support is disabled: " + reason;
                });
            } else {
                Meta.throwExceptionWithMessage(meta.java_lang_OutOfMemoryError, "Thread support is disabled: " + reason);
            }
        }
    }

    public static void terminate(StaticObject thread, Meta meta) {
        terminate(thread, null, meta);
    }

    private static void terminate(@Host(Thread.class) StaticObject self, DirectCallNode threadExit, Meta meta) {
        setThreadStop(self, KillStatus.EXITING);
        try {
            if (threadExit != null) {
                threadExit.call(self);
            } else {
                meta.java_lang_Thread_exit.invokeDirect(self);
            }
        } catch (EspressoException | EspressoExitException e) {
            // just drop it
        }
        self.getLock().lock();
        try {
            self.setIntField(meta.java_lang_Thread_threadStatus, State.TERMINATED.value);
            // Notify waiting threads you are done working
            self.getLock().signalAll();
        } finally {
            self.getLock().unlock();
        }

        EspressoContext context = meta.getContext();
        // Cleanup.
        context.unregisterThread(self);
    }

    public static String getThreadName(Meta meta, StaticObject thread) {
        if (thread == null) {
            return "<unknown>";
        } else {
            return meta.toHostString((StaticObject) meta.java_lang_Thread_name.get(thread));
        }
    }

    public static long getThreadId(Meta meta, StaticObject thread) {
        if (thread == null) {
            return -1;
        } else {
            return (long) meta.java_lang_Thread_tid.get(thread);
        }
    }

    @TruffleBoundary
    @Substitution
    public static void yield() {
        Thread.yield();
    }

    @SuppressWarnings("unused")
    @Substitution(hasReceiver = true)
    public static void setPriority0(@Host(Thread.class) StaticObject self, int newPriority) {
        // Priority is set in the guest field in Thread.setPriority().
        Thread hostThread = getHostFromGuestThread(self);
        if (hostThread == null) {
            return;
        }
        hostThread.setPriority(newPriority);
    }

    @Substitution(hasReceiver = true)
    public static boolean isAlive(@Host(Thread.class) StaticObject self) {
        int state = self.getIntField(self.getKlass().getMeta().java_lang_Thread_threadStatus);
        return state != State.NEW.value && state != State.TERMINATED.value;
    }

    @Substitution(hasReceiver = true)
    public static @Host(typeName = "Ljava/lang/Thread$State;") StaticObject getState(@Host(Thread.class) StaticObject self,
                    // Checkstyle: stop
                    @GuestCall(target = "sun_misc_VM_toThreadState") DirectCallNode toThreadState,
                    // Checkstyle: resume
                    @InjectMeta Meta meta) {
        return (StaticObject) toThreadState.call(self.getIntField(meta.java_lang_Thread_threadStatus));
    }

    @SuppressWarnings("unused")
    @Substitution
    public static void registerNatives() {
        /* nop */
    }

    @TruffleBoundary
    @Substitution
    public static boolean holdsLock(@Host(Object.class) StaticObject object, @InjectMeta Meta meta) {
        if (StaticObject.isNull(object)) {
            throw meta.throwNullPointerException();
        }
        return object.getLock().isHeldByCurrentThread();
    }

    @TruffleBoundary
    @Substitution
    public static void sleep(long millis, @InjectMeta Meta meta) {
        StaticObject thread = meta.getContext().getCurrentThread();
        try {
            fromRunnable(thread, meta, State.TIMED_WAITING);
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            setInterrupt(thread, false);
            throw Meta.throwExceptionWithMessage(meta.java_lang_InterruptedException, e.getMessage());
        } catch (IllegalArgumentException e) {
            throw Meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, e.getMessage());
        } finally {
            toRunnable(thread, meta, State.RUNNABLE);
        }
    }

    public static void setInterrupt(StaticObject self, boolean value) {
        self.setHiddenField(self.getKlass().getMeta().HIDDEN_INTERRUPTED, value);
    }

    static boolean checkInterrupt(StaticObject self) {
        Boolean interrupt = (Boolean) self.getHiddenField(self.getKlass().getMeta().HIDDEN_INTERRUPTED);
        return interrupt != null && interrupt;
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static void interrupt0(@Host(Object.class) StaticObject self) {
        Thread hostThread = getHostFromGuestThread(self);
        if (hostThread == null) {
            return;
        }
        setInterrupt(self, true);
        hostThread.interrupt();
    }

    @Substitution(hasReceiver = true)
    public static boolean isInterrupted(@Host(Thread.class) StaticObject self, boolean clear) {
        boolean result = checkInterrupt(self);
        if (clear) {
            Thread hostThread = getHostFromGuestThread(self);
            if (hostThread != null && hostThread.isInterrupted()) {
                try {
                    callHostThreadIsInterrupted(hostThread);
                } catch (Throwable e) {
                    throw EspressoError.shouldNotReachHere(e);
                }
            }
            setInterrupt(self, false);
        }
        return result;
    }

    @TruffleBoundary
    private static void callHostThreadIsInterrupted(Thread hostThread) throws IllegalAccessException, InvocationTargetException {
        isInterrupted.invoke(hostThread, true);
    }

    @TruffleBoundary
    @SuppressWarnings({"unused"})
    @Substitution(hasReceiver = true)
    public static void resume0(@Host(Object.class) StaticObject self) {
        SuspendLock lock = getSuspendLock(self);
        if (lock == null) {
            return;
        }
        synchronized (lock) {
            lock.shouldSuspend = false;
            lock.notifyAll();
        }
    }

    @TruffleBoundary
    @SuppressWarnings({"unused"})
    @Substitution(hasReceiver = true)
    public static void suspend0(@Host(Object.class) StaticObject toSuspend) {
        toSuspend.getKlass().getContext().invalidateNoSuspend("Calling Thread.suspend()");
        SuspendLock lock = getSuspendLock(toSuspend);
        if (lock == null) {
            if (!isAlive(toSuspend)) {
                return;
            }
            lock = initSuspendLock(toSuspend);
        }
        suspendHandshake(lock, toSuspend.getKlass().getMeta(), toSuspend);
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static void stop0(@Host(Object.class) StaticObject self, @Host(Object.class) StaticObject throwable) {
        self.getKlass().getContext().invalidateNoThreadStop("Calling thread.stop()");
        killThread(self);
        setInterrupt(self, true);
        setDeathThrowable(self, throwable);
        Thread hostThread = getHostFromGuestThread(self);
        if (hostThread == null) {
            return;
        }
        hostThread.interrupt();
    }

    @TruffleBoundary
    @Substitution(hasReceiver = true)
    public static void setNativeName(@Host(Object.class) StaticObject self, @Host(String.class) StaticObject name,
                    @InjectMeta Meta meta) {
        Thread hostThread = getHostFromGuestThread(self);
        hostThread.setName(meta.toHostString(name));
    }

    public static Thread getHostFromGuestThread(@Host(Object.class) StaticObject self) {
        return (Thread) self.getHiddenField(self.getKlass().getMeta().HIDDEN_HOST_THREAD);
    }

    public static boolean checkThreadStatus(StaticObject thread, KillStatus status) {
        KillStatus stop = (KillStatus) thread.getHiddenField(thread.getKlass().getMeta().HIDDEN_DEATH);
        return stop != null && stop == status;
    }

    public static void setThreadStop(StaticObject thread, KillStatus value) {
        thread.setHiddenField(thread.getKlass().getMeta().HIDDEN_DEATH, value);
    }

    /**
     * Hints the thread that it should throw a ThreadDeath error whenever he can.
     */
    public static void killThread(StaticObject thread) {
        setThreadStop(thread, KillStatus.KILL);
    }

    /**
     * Forces the thread to stop execution at the next safepoint by throwing a host exit exception.
     */
    public static void forceKillThread(StaticObject thread) {
        setThreadStop(thread, KillStatus.SHUTDOWN);
    }

    public static void setDeathThrowable(StaticObject self, Object deathThrowable) {
        self.setHiddenField(self.getKlass().getMeta().HIDDEN_DEATH_THROWABLE, deathThrowable);
    }

    public static Object getDeathThrowable(StaticObject self) {
        return self.getHiddenField(self.getKlass().getMeta().HIDDEN_DEATH_THROWABLE);
    }

    public static KillStatus getKillStatus(StaticObject thread) {
        return (KillStatus) thread.getHiddenField(thread.getKlass().getMeta().HIDDEN_DEATH);
    }

    public enum KillStatus {
        /**
         * Normal state: no Thread.stop() called, or ThreadDeath has already been thrown.
         */
        NORMAL,
        /**
         * Thread will throw an asynchronous ThreadDeath whenever possible.
         */
        KILL,
        /**
         * Was killed, but we are in context closing. If the thread is alive for a while in that
         * state, it will be considered uncooperative.
         */
        KILLED,
        /**
         * Was killed, and is calling Thread.exit(). Ignore further kill signals.
         */
        EXITING,
        /**
         * Thread is uncooperative: needs to be killed with a host exception. Very dangerous state
         * to be in.
         */
        SHUTDOWN
    }

    public static class SuspendLock {
        private Object notifier = new Object();

        private volatile boolean shouldSuspend;
        private volatile boolean threadSuspended;

        public boolean shouldSuspend() {
            return shouldSuspend;
        }

        public boolean targetThreadIsSuspended() {
            return threadSuspended;
        }
    }

    private static SuspendLock getSuspendLock(@Host(Object.class) StaticObject self) {
        return (SuspendLock) self.getHiddenField(self.getKlass().getMeta().HIDDEN_SUSPEND_LOCK);
    }

    /**
     * Synchronizes on Target_ class to avoid deadlock when locking on thread object.
     */
    private static synchronized SuspendLock initSuspendLock(@Host(Object.class) StaticObject self) {
        SuspendLock lock = getSuspendLock(self);
        if (lock == null) {
            lock = new SuspendLock();
            self.setHiddenField(self.getKlass().getMeta().HIDDEN_SUSPEND_LOCK, lock);
        }
        return lock;
    }

    public static boolean isSuspended(StaticObject self) {
        assert getSuspendLock(self) != null;
        return getSuspendLock(self).shouldSuspend();
    }

    @TruffleBoundary
    public static void trySuspend(StaticObject self) {
        SuspendLock lock = getSuspendLock(self);
        if (lock == null) {
            return;
        }
        synchronized (lock) {
            if (lock.shouldSuspend()) {
                synchronized (lock.notifier) {
                    lock.threadSuspended = true;
                    lock.notifier.notifyAll();
                }
            }
            while (lock.shouldSuspend()) {
                try {
                    lock.wait();

                } catch (InterruptedException e) {
                }
            }
        }
        lock.threadSuspended = false;
    }

    @TruffleBoundary
    private static void suspendHandshake(SuspendLock lock, Meta meta, StaticObject toSuspend) {
        Object notifier = lock.notifier;
        boolean wasInterrupted = false;
        while (!lock.targetThreadIsSuspended()) {
            lock.shouldSuspend = true;
            try {
                synchronized (notifier) {
                    if (toSuspend.getIntField(meta.java_lang_Thread_threadStatus) == State.RUNNABLE.value) {
                        notifier.wait();
                    } else {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                /* Thread.suspend() is not supposed to be interrupted */
                wasInterrupted = true;
            }
        }
        if (wasInterrupted) {
            interrupt0(meta.getContext().getCurrentThread());
        }
    }
}
