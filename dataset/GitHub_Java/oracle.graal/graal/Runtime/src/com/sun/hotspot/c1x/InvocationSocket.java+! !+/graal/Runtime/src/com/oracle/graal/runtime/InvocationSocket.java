/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.hotspot.c1x;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.sun.hotspot.c1x.logging.*;

/**
 * A collection of java.lang.reflect proxies that communicate over a socket connection.
 *
 * Calling a method sends the method name and the parameters through the socket. Afterwards this class waits for a
 * result. While waiting for a result three types of objects can arrive through the socket: a method invocation, a
 * method result or an exception. Method invocation can thus be recursive.
 *
 * @author Lukas Stadler
 */
public class InvocationSocket {

    private static final boolean DEBUG = false;
    private static final boolean COUNT_CALLS = false;

    private static final HashSet<String> cachedMethodNames = new HashSet<String>();
    private static final HashSet<String> forbiddenMethodNames = new HashSet<String>();

    static {
        cachedMethodNames.add("name");
        cachedMethodNames.add("kind");
        cachedMethodNames.add("isResolved");
        cachedMethodNames.add("getVMEntries");
        cachedMethodNames.add("exactType");
        cachedMethodNames.add("isInitialized");
        forbiddenMethodNames.add("javaClass");
    }

    private final ObjectOutputStream output;
    private final ObjectInputStream input;

    private final Map<String, Integer> counts = new HashMap<String, Integer>();

    public InvocationSocket(ObjectOutputStream output, ObjectInputStream input) {
        this.output = output;
        this.input = input;

        if (COUNT_CALLS) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    SortedMap<Integer, String> sorted = new TreeMap<Integer, String>();
                    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
                        sorted.put(entry.getValue(), entry.getKey());
                    }
                    for (Map.Entry<Integer, String> entry : sorted.entrySet()) {
                        System.out.println(entry.getKey() + ": " + entry.getValue());
                    }
                }
            });
        }
    }

    /**
     * Represents one invocation of a method that is transferred via the socket connection.
     *
     * @author Lukas Stadler
     */
    private static class Invocation implements Serializable {

        public Object receiver;
        public String methodName;
        public Object[] args;

        public Invocation(Object receiver, String methodName, Object[] args) {
            this.receiver = receiver;
            this.methodName = methodName;
            this.args = args;
        }
    }

    /**
     * Represents the result of an invocation that is transferred via the socket connection.
     *
     * @author Lukas Stadler
     */
    private static class Result implements Serializable {

        public Object result;

        public Result(Object result) {
            this.result = result;
        }
    }

    private void incCount(String name, Object[] args) {
        if (COUNT_CALLS) {
            name = name + (args == null ? 0 : args.length);
            if (counts.get(name) != null) {
                counts.put(name, counts.get(name) + 1);
            } else {
                counts.put(name, 1);
            }
        }
    }

    /**
     * Each instance of this class handles remote invocations for one instance of a Remote class. It will forward all
     * interface methods to the other end of the socket and cache the results of calls to certain methods.
     *
     * @author Lukas Stadler
     */
    public class Handler implements InvocationHandler {

        private final Object receiver;
        private final HashMap<String, Object> cache = new HashMap<String, Object>();

        public Handler(Object receiver) {
            this.receiver = receiver;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // only interface methods can be transferred, java.lang.Object methods
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(receiver, args);
            }
            String methodName = method.getName();
            // check if the result of this zero-arg method was cached
            if (args == null || args.length == 0) {
                if (cache.containsKey(methodName)) {
                    return cache.get(methodName);
                }
            }
            if (forbiddenMethodNames.contains(methodName)) {
                throw new IllegalAccessException(methodName + " not allowed");
            }
            Object result = null;
            try {
                if (DEBUG) {
                    Logger.startScope("invoking remote " + methodName);
                }
                incCount(methodName, args);

                output.writeObject(new Invocation(receiver, methodName, args));
                output.flush();
                result = waitForResult(false);

                // result caching for selected methods
                if ((args == null || args.length == 0) && cachedMethodNames.contains(methodName)) {
                    cache.put(methodName, result);
                }
                return result;
            } catch (Throwable t) {
                t.printStackTrace();
                throw t;
            } finally {
                if (DEBUG) {
                    Logger.endScope(" = " + result);
                }
            }
        }
    }

    /**
     * Waits for the result of a remote method invocation. Invocations that should be executed in this VM might arrive
     * while waiting for the result, and these invocations will be executed before again waiting fort he result.
     */
    public Object waitForResult(boolean eofExpected) throws IOException, ClassNotFoundException {
        while (true) {
            Object in;
            try {
                in = input.readObject();
            } catch(EOFException e) {
                if (eofExpected) {
                    return null;
                }
                throw e;
            }
            if (in instanceof Result) {
                return ((Result) in).result;
            } else if (in instanceof RuntimeException) {
                throw (RuntimeException) in;
            } else if (in instanceof Throwable) {
                throw new RuntimeException((Throwable) in);
            }

            Invocation invoke = (Invocation) in;
            Method method = null;
            for (Class<?> clazz = invoke.receiver.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
                for (Method m : clazz.getDeclaredMethods()) {
                    if (invoke.methodName.equals(m.getName())) {
                        method = m;
                        break;
                    }
                }
            }
            if (method == null) {
                Exception e = new UnsupportedOperationException("unknown method " + invoke.methodName);
                e.printStackTrace();
                output.writeObject(e);
                output.flush();
            } else {
                Object result = null;
                try {
                    if (invoke.args == null) {
                        if (DEBUG) {
                            Logger.startScope("invoking local " + invoke.methodName);
                        }
                        result = method.invoke(invoke.receiver);
                    } else {
                        if (Logger.ENABLED && DEBUG) {
                            StringBuilder str = new StringBuilder();
                            str.append("invoking local " + invoke.methodName + "(");
                            for (int i = 0; i < invoke.args.length; i++) {
                                str.append(i == 0 ? "" : ", ");
                                str.append(Logger.pretty(invoke.args[i]));
                            }
                            str.append(")");
                            Logger.startScope(str.toString());
                        }
                        result = method.invoke(invoke.receiver, invoke.args);
                    }
                    result = new Result(result);
                } catch (IllegalArgumentException e) {
                    System.out.println("error while invoking " + invoke.methodName);
                    e.getCause().printStackTrace();
                    result = e.getCause();
                } catch (InvocationTargetException e) {
                    System.out.println("error while invoking " + invoke.methodName);
                    e.getCause().printStackTrace();
                    result = e.getCause();
                } catch (IllegalAccessException e) {
                    System.out.println("error while invoking " + invoke.methodName);
                    e.getCause().printStackTrace();
                    result = e.getCause();
                } finally {
                    if (DEBUG) {
                        if (result instanceof Result) {
                            Logger.endScope(" = " + ((Result)result).result);
                        } else {
                            Logger.endScope(" = " + result);
                        }
                    }
                }
                output.writeObject(result);
                output.flush();
            }
        }
    }

    /**
     * Sends a result without invoking a method, used by CompilationServer startup code.
     */
    public void sendResult(Object obj) throws IOException {
        output.writeObject(new Result(obj));
        output.flush();
    }
}
