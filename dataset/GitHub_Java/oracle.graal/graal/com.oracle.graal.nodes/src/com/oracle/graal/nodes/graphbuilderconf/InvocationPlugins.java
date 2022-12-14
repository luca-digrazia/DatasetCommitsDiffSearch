/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.graphbuilderconf;

import static java.lang.String.format;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import com.oracle.graal.graph.Node;
import com.oracle.graal.graph.iterators.NodeIterable;
import com.oracle.graal.nodes.ValueNode;

/**
 * Manages a set of {@link InvocationPlugin}s.
 */
public class InvocationPlugins {

    public static class InvocationPluginReceiver implements InvocationPlugin.Receiver {
        private final GraphBuilderContext parser;
        private ValueNode[] args;
        private ValueNode value;

        public InvocationPluginReceiver(GraphBuilderContext parser) {
            this.parser = parser;
        }

        @Override
        public ValueNode get() {
            assert args != null : "Cannot get the receiver of a static method";
            if (value == null) {
                value = parser.nullCheckedValue(args[0]);
                if (value != args[0]) {
                    args[0] = value;
                }
            }
            return value;
        }

        @Override
        public boolean isConstant() {
            return args[0].isConstant();
        }

        public InvocationPluginReceiver init(ResolvedJavaMethod targetMethod, ValueNode[] newArgs) {
            if (!targetMethod.isStatic()) {
                this.args = newArgs;
                this.value = null;
                return this;
            }
            return null;
        }
    }

    /**
     * Utility for
     * {@linkplain InvocationPlugins#register(InvocationPlugin, Class, String, Class...)
     * registration} of invocation plugins.
     */
    public static class Registration {

        private final InvocationPlugins plugins;
        private final Class<?> declaringClass;
        private boolean allowOverwrite;

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param declaringClass the class declaring the methods for which plugins will be
         *            registered via this object
         */
        public Registration(InvocationPlugins plugins, Class<?> declaringClass) {
            this.plugins = plugins;
            this.declaringClass = declaringClass;
        }

        /**
         * Creates an object for registering {@link InvocationPlugin}s for methods declared by a
         * given class.
         *
         * @param plugins where to register the plugins
         * @param declaringClassName the name of the class class declaring the methods for which
         *            plugins will be registered via this object
         */
        public Registration(InvocationPlugins plugins, String declaringClassName) {
            this.plugins = plugins;
            try {
                this.declaringClass = Class.forName(declaringClassName);
            } catch (ClassNotFoundException ex) {
                throw JVMCIError.shouldNotReachHere(ex);
            }
        }

        /**
         * Configures this registration to allow or disallow overwriting of invocation plugins.
         */
        public Registration setAllowOverwrite(boolean allowOverwrite) {
            this.allowOverwrite = allowOverwrite;
            return this;
        }

        /**
         * Registers a plugin for a method with no arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register0(String name, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringClass, name);
        }

        /**
         * Registers a plugin for a method with 1 argument.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register1(String name, Class<?> arg, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringClass, name, arg);
        }

        /**
         * Registers a plugin for a method with 2 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register2(String name, Class<?> arg1, Class<?> arg2, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringClass, name, arg1, arg2);
        }

        /**
         * Registers a plugin for a method with 3 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register3(String name, Class<?> arg1, Class<?> arg2, Class<?> arg3, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringClass, name, arg1, arg2, arg3);
        }

        /**
         * Registers a plugin for a method with 4 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register4(String name, Class<?> arg1, Class<?> arg2, Class<?> arg3, Class<?> arg4, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringClass, name, arg1, arg2, arg3, arg4);
        }

        /**
         * Registers a plugin for a method with 5 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void register5(String name, Class<?> arg1, Class<?> arg2, Class<?> arg3, Class<?> arg4, Class<?> arg5, InvocationPlugin plugin) {
            plugins.register(plugin, false, allowOverwrite, declaringClass, name, arg1, arg2, arg3, arg4, arg5);
        }

        /**
         * Registers a plugin for an optional method with no arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional0(String name, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringClass, name);
        }

        /**
         * Registers a plugin for an optional method with 1 argument.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional1(String name, Class<?> arg, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringClass, name, arg);
        }

        /**
         * Registers a plugin for an optional method with 2 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional2(String name, Class<?> arg1, Class<?> arg2, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringClass, name, arg1, arg2);
        }

        /**
         * Registers a plugin for an optional method with 3 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional3(String name, Class<?> arg1, Class<?> arg2, Class<?> arg3, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringClass, name, arg1, arg2, arg3);
        }

        /**
         * Registers a plugin for an optional method with 4 arguments.
         *
         * @param name the name of the method
         * @param plugin the plugin to be registered
         */
        public void registerOptional4(String name, Class<?> arg1, Class<?> arg2, Class<?> arg3, Class<?> arg4, InvocationPlugin plugin) {
            plugins.register(plugin, true, allowOverwrite, declaringClass, name, arg1, arg2, arg3, arg4);
        }

        /**
         * Registers a plugin that implements a method based on the bytecode of a substitute method.
         *
         * @param substituteDeclaringClass the class declaring the substitute method
         * @param name the name of both the original and substitute method
         * @param argumentTypes the argument types of the method. Element 0 of this array must be
         *            the {@link Class} value for {@link InvocationPlugin.Receiver} iff the method
         *            is non-static. Upon returning, element 0 will have been rewritten to
         *            {@code declaringClass}
         */
        public void registerMethodSubstitution(Class<?> substituteDeclaringClass, String name, Class<?>... argumentTypes) {
            registerMethodSubstitution(substituteDeclaringClass, name, name, argumentTypes);
        }

        /**
         * Registers a plugin that implements a method based on the bytecode of a substitute method.
         *
         * @param substituteDeclaringClass the class declaring the substitute method
         * @param name the name of both the original method
         * @param substituteName the name of the substitute method
         * @param argumentTypes the argument types of the method. Element 0 of this array must be
         *            the {@link Class} value for {@link InvocationPlugin.Receiver} iff the method
         *            is non-static. Upon returning, element 0 will have been rewritten to
         *            {@code declaringClass}
         */
        public void registerMethodSubstitution(Class<?> substituteDeclaringClass, String name, String substituteName, Class<?>... argumentTypes) {
            MethodSubstitutionPlugin plugin = new MethodSubstitutionPlugin(substituteDeclaringClass, substituteName, argumentTypes);
            plugins.register(plugin, false, allowOverwrite, declaringClass, name, argumentTypes);
        }
    }

    /**
     * Key for a method.
     */
    static class MethodKey {
        final boolean isStatic;

        /**
         * This method is optional. This is used for new API methods not present in previous JDK
         * versions.
         */
        final boolean isOptional;

        final Class<?> declaringClass;
        final String name;
        final Class<?>[] argumentTypes;
        final InvocationPlugin value;

        MethodKey(InvocationPlugin data, boolean isStatic, boolean isOptional, Class<?> declaringClass, String name, Class<?>... argumentTypes) {
            assert isStatic || argumentTypes[0] == declaringClass;
            this.value = data;
            this.isStatic = isStatic;
            this.isOptional = isOptional;
            this.declaringClass = declaringClass;
            this.name = name;
            this.argumentTypes = argumentTypes;
            assert isOptional || resolveJava() != null;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof MethodKey) {
                MethodKey that = (MethodKey) obj;
                boolean res = this.name.equals(that.name) && this.declaringClass.equals(that.declaringClass) && Arrays.equals(this.argumentTypes, that.argumentTypes);
                assert !res || this.isStatic == that.isStatic;
                return res;
            }
            return false;
        }

        public int getDeclaredParameterCount() {
            return isStatic ? argumentTypes.length : argumentTypes.length - 1;
        }

        @Override
        public int hashCode() {
            // Replay compilation mandates use of stable hash codes
            return declaringClass.getName().hashCode() ^ name.hashCode();
        }

        private ResolvedJavaMethod resolve(MetaAccessProvider metaAccess) {
            Executable method = resolveJava();
            if (method == null) {
                return null;
            }
            return metaAccess.lookupJavaMethod(method);
        }

        private Executable resolveJava() {
            try {
                Executable res;
                Class<?>[] parameterTypes = isStatic ? argumentTypes : Arrays.copyOfRange(argumentTypes, 1, argumentTypes.length);
                if (name.equals("<init>")) {
                    res = declaringClass.getDeclaredConstructor(parameterTypes);
                } else {
                    res = declaringClass.getDeclaredMethod(name, parameterTypes);
                }
                assert Modifier.isStatic(res.getModifiers()) == isStatic;
                return res;
            } catch (NoSuchMethodException | SecurityException e) {
                if (isOptional) {
                    return null;
                }
                throw new InternalError(e);
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(declaringClass.getName()).append('.').append(name).append('(');
            for (Class<?> p : argumentTypes) {
                if (sb.charAt(sb.length() - 1) != '(') {
                    sb.append(", ");
                }
                sb.append(p.getSimpleName());
            }
            return sb.append(')').toString();
        }
    }

    private final MetaAccessProvider metaAccess;

    /**
     * Initial list of entries.
     */
    private final List<MethodKey> registrations = new ArrayList<>(INITIAL_CAPACITY);

    /**
     * Deferred registrations as well as guard for initialization of {@link #entries}. The guard
     * uses double-checked locking which is why this field is {@code volatile}.
     */
    private volatile List<Runnable> deferredRegistrations = new ArrayList<>();

    /**
     * Adds a {@link Runnable} for doing registration deferred until the first time
     * {@link #get(ResolvedJavaMethod)} or {@link #closeRegistration()} is called on this object.
     */
    public void defer(Runnable deferrable) {
        assert entries == null && deferredRegistrations != null : "registration is closed";
        deferredRegistrations.add(deferrable);
    }

    /**
     * Entry map that is initialized by {@link #initializeMap()}.
     */
    private Map<ResolvedJavaMethod, InvocationPlugin> entries;

    private static final int INITIAL_CAPACITY = 64;

    /**
     * Adds an entry to this map for a specified method.
     *
     * @param value value to be associated with the specified method
     * @param isStatic specifies if the method is static
     * @param isOptional specifies if the method is optional
     * @param declaringClass the class declaring the method
     * @param name the name of the method
     * @param argumentTypes the argument types of the method. Element 0 of this array must be
     *            {@code declaringClass} iff the method is non-static.
     * @return an object representing the method
     */
    MethodKey put(InvocationPlugin value, boolean isStatic, boolean isOptional, boolean allowOverwrite, Class<?> declaringClass, String name, Class<?>... argumentTypes) {
        assert isStatic || argumentTypes[0] == declaringClass;
        MethodKey methodKey = new MethodKey(value, isStatic, isOptional, declaringClass, name, argumentTypes);
        assert entries == null : "registration is closed";
        assert allowOverwrite || !registrations.contains(methodKey) : "a value is already registered for " + methodKey;
        registrations.add(methodKey);
        return methodKey;
    }

    /**
     * Determines if a method denoted by a given {@link MethodKey} is in this map.
     */
    boolean containsKey(MethodKey key) {
        return registrations.contains(key);
    }

    InvocationPlugin get(ResolvedJavaMethod method) {
        if (deferredRegistrations != null) {
            initializeMap();
        }
        return entries.get(method);
    }

    /**
     * Disallows new registrations of new plugins, and creates the internal tables for method
     * lookup.
     */
    public void closeRegistration() {
        if (deferredRegistrations != null) {
            initializeMap();
        }
    }

    void initializeMap() {
        if (deferredRegistrations != null) {
            synchronized (this) {
                if (deferredRegistrations != null) {
                    List<Runnable> localDeferredRegistrations = deferredRegistrations;
                    for (Runnable deferrable : localDeferredRegistrations) {
                        deferrable.run();
                    }
                    if (registrations.isEmpty()) {
                        entries = Collections.emptyMap();
                    } else {
                        Map<ResolvedJavaMethod, InvocationPlugin> newEntries = new HashMap<>();
                        for (MethodKey methodKey : registrations) {
                            ResolvedJavaMethod m = methodKey.resolve(metaAccess);
                            newEntries.put(m, methodKey.value);
                        }
                        entries = newEntries;
                    }
                    deferredRegistrations = null;
                }
            }
        }
    }

    public int size() {
        return registrations.size();
    }

    /**
     * The plugins {@linkplain #lookupInvocation(ResolvedJavaMethod) searched} before searching in
     * this object.
     */
    protected final InvocationPlugins parent;

    private InvocationPlugins(InvocationPlugins parent, MetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;
        InvocationPlugins p = parent;
        this.parent = p;
    }

    /**
     * Creates a set of invocation plugins with a non-null {@linkplain #getParent() parent}.
     */
    public InvocationPlugins(InvocationPlugins parent) {
        this(parent, parent.getMetaAccess());
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    public InvocationPlugins(MetaAccessProvider metaAccess) {
        this(null, metaAccess);
    }

    private void register(InvocationPlugin plugin, boolean isOptional, boolean allowOverwrite, Class<?> declaringClass, String name, Class<?>... argumentTypes) {
        boolean isStatic = argumentTypes.length == 0 || argumentTypes[0] != InvocationPlugin.Receiver.class;
        if (!isStatic) {
            argumentTypes[0] = declaringClass;
        }
        MethodKey methodInfo = put(plugin, isStatic, isOptional, allowOverwrite, declaringClass, name, argumentTypes);
        assert Checker.check(this, methodInfo, plugin);
    }

    /**
     * Registers an invocation plugin for a given method. There must be no plugin currently
     * registered for {@code method}.
     *
     * @param argumentTypes the argument types of the method. Element 0 of this array must be the
     *            {@link Class} value for {@link InvocationPlugin.Receiver} iff the method is
     *            non-static. Upon returning, element 0 will have been rewritten to
     *            {@code declaringClass}
     */
    public void register(InvocationPlugin plugin, Class<?> declaringClass, String name, Class<?>... argumentTypes) {
        register(plugin, false, false, declaringClass, name, argumentTypes);
    }

    /**
     * Registers an invocation plugin for a given, optional method. There must be no plugin
     * currently registered for {@code method}.
     *
     * @param argumentTypes the argument types of the method. Element 0 of this array must be the
     *            {@link Class} value for {@link InvocationPlugin.Receiver} iff the method is
     *            non-static. Upon returning, element 0 will have been rewritten to
     *            {@code declaringClass}
     */
    public void registerOptional(InvocationPlugin plugin, Class<?> declaringClass, String name, Class<?>... argumentTypes) {
        register(plugin, true, false, declaringClass, name, argumentTypes);
    }

    /**
     * Gets the plugin for a given method.
     *
     * @param method the method to lookup
     * @return the plugin associated with {@code method} or {@code null} if none exists
     */
    public InvocationPlugin lookupInvocation(ResolvedJavaMethod method) {
        if (parent != null) {
            InvocationPlugin plugin = parent.lookupInvocation(method);
            if (plugin != null) {
                return plugin;
            }
        }
        return get(method);
    }

    /**
     * Gets the invocation plugins {@linkplain #lookupInvocation(ResolvedJavaMethod) searched}
     * before searching in this object.
     */
    public InvocationPlugins getParent() {
        return parent;
    }

    @Override
    public String toString() {
        return registrations.stream().map(MethodKey::toString).collect(Collectors.joining(", ")) + " / parent: " + this.parent;
    }

    private static class Checker {
        private static final int MAX_ARITY = 5;
        /**
         * The set of all {@link InvocationPlugin#apply} method signatures.
         */
        static final Class<?>[][] SIGS;

        static {
            ArrayList<Class<?>[]> sigs = new ArrayList<>(MAX_ARITY);
            for (Method method : InvocationPlugin.class.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) && method.getName().equals("apply")) {
                    Class<?>[] sig = method.getParameterTypes();
                    assert sig[0] == GraphBuilderContext.class;
                    assert sig[1] == ResolvedJavaMethod.class;
                    assert sig[2] == InvocationPlugin.Receiver.class;
                    assert Arrays.asList(sig).subList(3, sig.length).stream().allMatch(c -> c == ValueNode.class);
                    while (sigs.size() < sig.length - 2) {
                        sigs.add(null);
                    }
                    sigs.set(sig.length - 3, sig);
                }
            }
            assert sigs.indexOf(null) == -1 : format("need to add an apply() method to %s that takes %d %s arguments ", InvocationPlugin.class.getName(), sigs.indexOf(null),
                            ValueNode.class.getSimpleName());
            SIGS = sigs.toArray(new Class<?>[sigs.size()][]);
        }

        public static boolean check(InvocationPlugins plugins, MethodKey method, InvocationPlugin plugin) {
            InvocationPlugins p = plugins.parent;
            while (p != null) {
                assert !p.containsKey(method) : "a plugin is already registered for " + method;
                p = p.parent;
            }
            if (plugin instanceof ForeignCallPlugin || plugin instanceof GeneratedInvocationPlugin) {
                return true;
            }
            if (plugin instanceof MethodSubstitutionPlugin) {
                MethodSubstitutionPlugin msplugin = (MethodSubstitutionPlugin) plugin;
                msplugin.getJavaSubstitute();
                return true;
            }
            int arguments = method.getDeclaredParameterCount();
            assert arguments < SIGS.length : format("need to extend %s to support method with %d arguments: %s", InvocationPlugin.class.getSimpleName(), arguments, method);
            for (Method m : plugin.getClass().getDeclaredMethods()) {
                if (m.getName().equals("apply")) {
                    Class<?>[] parameterTypes = m.getParameterTypes();
                    if (Arrays.equals(SIGS[arguments], parameterTypes)) {
                        return true;
                    }
                }
            }
            throw new AssertionError(format("graph builder plugin for %s not found", method));
        }
    }

    /**
     * Checks a set of nodes added to the graph by an {@link InvocationPlugin}.
     *
     * @param b the graph builder that applied the plugin
     * @param plugin a plugin that was just applied
     * @param newNodes the nodes added to the graph by {@code plugin}
     * @throws AssertionError if any check fail
     */
    public void checkNewNodes(GraphBuilderContext b, InvocationPlugin plugin, NodeIterable<Node> newNodes) {
        if (parent != null) {
            parent.checkNewNodes(b, plugin, newNodes);
        }
    }
}
