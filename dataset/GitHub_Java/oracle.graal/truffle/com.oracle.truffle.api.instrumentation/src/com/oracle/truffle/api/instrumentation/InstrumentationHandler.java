/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.api.instrumentation;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.instrumentation.InstrumentableFactory.WrapperNode;
import com.oracle.truffle.api.instrumentation.ProbeNode.EventChainNode;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Central coordinator class for the Truffle instrumentation framework. Allocated once per
 * {@linkplain com.oracle.truffle.api.vm.PolyglotEngine engine}.
 */
final class InstrumentationHandler {

    /* Enable trace output to stdout. */
    private static final boolean TRACE = Boolean.getBoolean("truffle.instrumentation.trace");

    /* Load order needs to be preserved for sources, thats why we cannot use WeakHashMap. */
    private final Map<Source, Void> sources = Collections.synchronizedMap(new WeakHashMap<Source, Void>());
    private final WeakAsyncList<Source> sourcesList = new WeakAsyncList<>(16);

    private final WeakAsyncList<RootNode> loadedRoots = new WeakAsyncList<>(256);
    private final WeakAsyncList<RootNode> executedRoots = new WeakAsyncList<>(64);

    private final EventBindingList executionBindings = new EventBindingList(8);
    private final EventBindingList sourceSectionBindings = new EventBindingList(8);
    private final EventBindingList sourceBindings = new EventBindingList(8);

    /*
     * Fast lookup of instrumenter instances based on a key provided by the accessor.
     */
    private final Map<Object, AbstractInstrumenter> instrumenterMap = new ConcurrentHashMap<>();

    /* Has the instrumentation framework been initialized? */
    private volatile boolean instrumentationInitialized;

    private final OutputStream out;
    private final OutputStream err;
    private final InputStream in;
    private final Map<Class<?>, Set<Class<?>>> cachedProvidedTags = new ConcurrentHashMap<>();

    private InstrumentationHandler(OutputStream out, OutputStream err, InputStream in) {
        this.out = out;
        this.err = err;
        this.in = in;
    }

    void onLoad(RootNode root) {
        if (!AccessorInstrumentHandler.nodesAccess().isInstrumentable(root)) {
            return;
        }
        if (!instrumentationInitialized) {
            initializeInstrumentation();
        }

        SourceSection sourceSection = root.getSourceSection();
        if (sourceSection != null) {
            // notify sources
            Source source = sourceSection.getSource();
            boolean isNewSource = false;
            synchronized (sources) {
                if (!sources.containsKey(source)) {
                    sources.put(source, null);
                    sourcesList.add(new WeakReference<>(source));
                    isNewSource = true;
                }
            }
            // we don't want to invoke foreign code while we are holding a lock to avoid deadlocks.
            if (isNewSource) {
                notifySourceBindingsLoaded(sourceBindings, source);
            }
        }
        loadedRoots.add(new WeakReference<>(root));

        // fast path no bindings attached
        if (!sourceSectionBindings.isEmpty()) {
            visitRoot(root, new NotifyLoadedListenerVisitor(sourceSectionBindings));
        }

    }

    void onFirstExecution(RootNode root) {
        if (!AccessorInstrumentHandler.nodesAccess().isInstrumentable(root)) {
            return;
        }
        if (!instrumentationInitialized) {
            initializeInstrumentation();
        }
        executedRoots.add(new WeakReference<>(root));

        // fast path no bindings attached
        if (executionBindings.isEmpty()) {
            return;
        }

        visitRoot(root, new InsertWrappersVisitor(executionBindings));
    }

    void addInstrument(Object key, Class<?> clazz) {
        addInstrumenter(key, new InstrumentClientInstrumenter(clazz, out, err, in));
    }

    void disposeInstrumenter(Object key, boolean cleanupRequired) {
        if (TRACE) {
            trace("BEGIN: Dispose instrumenter %n", key);
        }

        AbstractInstrumenter disposedInstrumenter = instrumenterMap.get(key);
        if (disposedInstrumenter != null) {
            instrumenterMap.remove(key);
        }

        if (disposedInstrumenter != null) {
            disposedInstrumenter.dispose();

            if (cleanupRequired) {
                EventBindingList disposedExecutionBindings = filterBindingsForInstrumenter(executionBindings, disposedInstrumenter);
                if (!disposedExecutionBindings.isEmpty()) {
                    visitRoots(executedRoots, new DisposeWrappersWithBindingVisitor(disposedExecutionBindings));
                }
                disposeBindingsBulk(disposedExecutionBindings);
                disposeBindingsBulk(filterBindingsForInstrumenter(sourceSectionBindings, disposedInstrumenter));
                disposeBindingsBulk(filterBindingsForInstrumenter(sourceBindings, disposedInstrumenter));
            }
        }

        if (TRACE) {
            trace("END: Disposed instrumenter %n", key);
        }
    }

    private static void disposeBindingsBulk(EventBindingList list) {
        AtomicReferenceArray<EventBinding<?>> bindingArray = list.getArray();
        for (int i = 0; i < bindingArray.length(); i++) {
            EventBinding<?> binding = bindingArray.get(i);
            if (binding == null) {
                // end of list
                break;
            }
            binding.disposeBulk();
        }
    }

    Instrumenter forLanguage(TruffleLanguage.Env context, TruffleLanguage<?> language) {
        return new LanguageClientInstrumenter<>(language, context);
    }

    void detachLanguage(Object context) {
        if (instrumenterMap.containsKey(context)) {
            disposeInstrumenter(context, false);
        }
    }

    <T> EventBinding<T> addExecutionBinding(EventBinding<T> binding) {
        if (TRACE) {
            trace("BEGIN: Adding execution binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        this.executionBindings.add(binding);

        if (instrumentationInitialized) {
            visitRoots(executedRoots, new InsertWrappersWithBindingVisitor(binding));
        }

        if (TRACE) {
            trace("END: Added execution binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        return binding;
    }

    <T> EventBinding<T> addSourceSectionBinding(EventBinding<T> binding, boolean notifyLoaded) {
        if (TRACE) {
            trace("BEGIN: Adding binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        this.sourceSectionBindings.add(binding);
        if (instrumentationInitialized && notifyLoaded) {
            visitRoots(loadedRoots, new NotifyLoadedWithBindingVisitor(binding));
        }

        if (TRACE) {
            trace("END: Added binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        return binding;
    }

    <T> EventBinding<T> addSourceBinding(EventBinding<T> binding, boolean notifyLoaded) {
        if (TRACE) {
            trace("BEGIN: Adding source binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        this.sourceBindings.add(binding);
        if (instrumentationInitialized && notifyLoaded) {
            AtomicReferenceArray<WeakReference<Source>> sourcesArray = sourcesList.getArray();
            for (int i = 0; i < sourcesArray.length(); i++) {
                WeakReference<Source> ref = sourcesArray.get(i);
                if (ref == null) {
                    break;
                }
                Source source = ref.get();
                if (source == null) {
                    continue;
                }
                notifySourceBindingLoaded(binding, source);
            }
        }

        if (TRACE) {
            trace("END: Added source binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        return binding;
    }

    private void visitRoots(WeakAsyncList<RootNode> roots, AbstractNodeVisitor addBindingsVisitor) {
        AtomicReferenceArray<WeakReference<RootNode>> array = roots.getArray();
        for (int i = 0; i < array.length(); i++) {
            WeakReference<RootNode> ref = array.get(i);
            if (ref == null) {
                // reached end of list
                break;
            }
            RootNode root = ref.get();
            if (root == null) {
                // gc'ed
                continue;
            }
            visitRoot(root, addBindingsVisitor);
        }
    }

    void disposeBinding(EventBinding<?> binding) {
        if (TRACE) {
            trace("BEGIN: Dispose binding %s, %s%n", binding.getFilter(), binding.getElement());
        }

        if (binding.isExecutionEvent()) {
            visitRoots(executedRoots, new DisposeWrappersVisitor(binding));
        }

        if (TRACE) {
            trace("END: Disposed binding %s, %s%n", binding.getFilter(), binding.getElement());
        }
    }

    EventChainNode installBindings(ProbeNode probeNodeImpl) {
        EventContext context = probeNodeImpl.getContext();
        SourceSection sourceSection = context.getInstrumentedSourceSection();
        if (TRACE) {
            trace("BEGIN: Lazy update for %s%n", sourceSection);
        }

        RootNode rootNode = probeNodeImpl.getRootNode();
        Node instrumentedNode = probeNodeImpl.findWrapper().getDelegateNode();
        Set<Class<?>> providedTags = getProvidedTags(rootNode);
        EventChainNode root = null;
        EventChainNode parent = null;

        AtomicReferenceArray<EventBinding<?>> bindingsArray = executionBindings.getArray();
        for (int i = 0; i < bindingsArray.length(); i++) {
            EventBinding<?> binding = bindingsArray.get(i);
            if (binding == null) {
                // end of list
                break;
            } else if (binding.isDisposed()) {
                // non alive element found
                continue;
            }
            if (binding.isInstrumentedFull(providedTags, rootNode, instrumentedNode, sourceSection)) {
                if (TRACE) {
                    trace("  Found binding %s, %s%n", binding.getFilter(), binding.getElement());
                }
                EventChainNode next = probeNodeImpl.createEventChainCallback(binding);
                if (next == null) {
                    continue;
                }

                if (root == null) {
                    root = next;
                } else {
                    assert parent != null;
                    parent.setNext(next);
                }
                parent = next;
            }
        }

        if (TRACE) {
            trace("END: Lazy updated for %s%n", sourceSection);
        }
        return root;
    }

    private static void notifySourceBindingsLoaded(EventBindingList bindings, Source source) {
        AtomicReferenceArray<EventBinding<?>> bindingArray = bindings.getArray();

        for (int i = 0; i < bindingArray.length(); i++) {
            EventBinding<?> binding = bindingArray.get(i);
            if (binding == null) {
                // end of list
                break;
            } else if (binding.isDisposed()) {
                continue;
            }
            notifySourceBindingLoaded(binding, source);
        }

    }

    private static void notifySourceBindingLoaded(EventBinding<?> binding, Source source) {
        if (!binding.isDisposed() && binding.isInstrumentedSource(source)) {
            try {
                ((LoadSourceEventListener) binding.getElement()).onLoad(source);
            } catch (Throwable t) {
                if (binding.isLanguageBinding()) {
                    throw t;
                } else {
                    ProbeNode.exceptionEventForClientInstrument(binding, "onLoad", t);
                }
            }
        }
    }

    static void notifySourceSectionLoaded(EventBinding<?> binding, Node node, SourceSection section) {
        LoadSourceSectionEventListener listener = (LoadSourceSectionEventListener) binding.getElement();
        try {
            listener.onLoad(section, node);
        } catch (Throwable t) {
            if (binding.isLanguageBinding()) {
                throw t;
            } else {
                ProbeNode.exceptionEventForClientInstrument(binding, "onLoad", t);
            }
        }
    }

    private synchronized void initializeInstrumentation() {
        if (!instrumentationInitialized) {
            if (TRACE) {
                trace("BEGIN: Initialize instrumentation%n");
            }
            for (AbstractInstrumenter instrumenter : instrumenterMap.values()) {
                instrumenter.initialize();
            }
            if (TRACE) {
                trace("END: Initialized instrumentation%n");
            }
            instrumentationInitialized = true;
        }
    }

    private void addInstrumenter(Object key, AbstractInstrumenter instrumenter) throws AssertionError {
        if (instrumenterMap.containsKey(key)) {
            return;
        }
        if (instrumentationInitialized) {
            instrumenter.initialize();
        }
        instrumenterMap.put(key, instrumenter);
    }

    private static EventBindingList filterBindingsForInstrumenter(EventBindingList bindings, AbstractInstrumenter instrumenter) {
        if (bindings.isEmpty()) {
            return EventBindingList.EMPTY;
        }
        EventBindingList newBindings = new EventBindingList(16);
        AtomicReferenceArray<EventBinding<?>> bindingsArray = bindings.getArray();
        for (int i = 0; i < bindingsArray.length(); i++) {
            EventBinding<?> binding = bindingsArray.get(i);
            if (binding == null) {
                break;
            }
            if (binding.isDisposed()) {
                continue;
            }
            if (binding.getInstrumenter() == instrumenter) {
                newBindings.add(binding);
            }
        }
        return newBindings;
    }

    @SuppressWarnings("unchecked")
    private void insertWrapper(Node instrumentableNode, SourceSection sourceSection) {
        Node node = instrumentableNode;
        Node parent = node.getParent();
        if (parent instanceof WrapperNode) {
            // already wrapped, need to invalidate the wrapper something changed
            invalidateWrapperImpl((WrapperNode) parent, node);
            return;
        }
        ProbeNode probe = new ProbeNode(InstrumentationHandler.this, sourceSection);
        WrapperNode wrapper;
        try {
            Class<?> factory = null;
            Class<?> currentClass = instrumentableNode.getClass();
            while (currentClass != null) {
                Instrumentable instrumentable = currentClass.getAnnotation(Instrumentable.class);
                if (instrumentable != null) {
                    factory = instrumentable.factory();
                    break;
                }
                currentClass = currentClass.getSuperclass();
            }

            if (factory == null) {
                if (TRACE) {
                    trace("No wrapper inserted for %s, section %s. Not annotated with @Instrumentable.%n", node, sourceSection);
                }
                // node or superclass is not annotated with @Instrumentable
                return;
            }

            if (TRACE) {
                trace("Insert wrapper for %s, section %s%n", node, sourceSection);
            }

            wrapper = ((InstrumentableFactory<Node>) factory.newInstance()).createWrapper(instrumentableNode, probe);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create wrapper node. ", e);
        }

        if (!(wrapper instanceof Node)) {
            throw new IllegalStateException(String.format("Implementation of %s must be a subclass of %s.", WrapperNode.class.getSimpleName(), Node.class.getSimpleName()));
        }

        Node wrapperNode = (Node) wrapper;
        if (wrapperNode.getParent() != null) {
            throw new IllegalStateException(String.format("Instance of provided %s is already adopted by another parent.", WrapperNode.class.getSimpleName()));
        }
        if (parent == null) {
            throw new IllegalStateException(String.format("Instance of instrumentable %s is not adopted by a parent.", Node.class.getSimpleName()));
        }

        if (!node.isSafelyReplaceableBy(wrapperNode)) {
            throw new IllegalStateException(String.format("WrapperNode implementation %s cannot be safely replaced in parent node class %s.", wrapperNode.getClass().getName(),
                            parent.getClass().getName()));
        }
        node.replace(wrapperNode);
        if (node.getParent() != wrapperNode) {
            throw new IllegalStateException("InstrumentableNode must have a WrapperNode as parent after createInstrumentationWrappwer is invoked.");
        }
    }

    private <T extends ExecutionEventNodeFactory> EventBinding<T> attachFactory(AbstractInstrumenter instrumenter, SourceSectionFilter filter, T factory) {
        return addExecutionBinding(new EventBinding<>(instrumenter, filter, factory, true));
    }

    private <T extends ExecutionEventListener> EventBinding<T> attachListener(AbstractInstrumenter instrumenter, SourceSectionFilter filter, T listener) {
        return addExecutionBinding(new EventBinding<>(instrumenter, filter, listener, true));
    }

    private <T> EventBinding<T> attachSourceListener(AbstractInstrumenter abstractInstrumenter, SourceSectionFilter filter, T listener, boolean notifyLoaded) {
        return addSourceBinding(new EventBinding<>(abstractInstrumenter, filter, listener, false), notifyLoaded);
    }

    private <T> EventBinding<T> attachSourceSectionListener(AbstractInstrumenter abstractInstrumenter, SourceSectionFilter filter, T listener, boolean notifyLoaded) {
        return addSourceSectionBinding(new EventBinding<>(abstractInstrumenter, filter, listener, false), notifyLoaded);
    }

    Set<Class<?>> getProvidedTags(Class<?> language) {
        Set<Class<?>> tags = cachedProvidedTags.get(language);
        if (tags == null) {
            ProvidedTags languageTags = language.getAnnotation(ProvidedTags.class);
            List<Class<?>> languageTagsList = languageTags != null ? Arrays.asList(languageTags.value()) : Collections.<Class<?>> emptyList();
            tags = Collections.unmodifiableSet(new HashSet<>(languageTagsList));
            cachedProvidedTags.put(language, tags);
        }
        return tags;
    }

    Set<Class<?>> getProvidedTags(RootNode root) {
        Class<?> language = AccessorInstrumentHandler.nodesAccess().findLanguage(root);
        if (language != null) {
            return getProvidedTags(language);
        } else {
            return Collections.emptySet();
        }
    }

    private static boolean isInstrumentableNode(Node node, SourceSection sourceSection) {
        return !(node instanceof WrapperNode) && !(node instanceof RootNode) && sourceSection != null;
    }

    private static void trace(String message, Object... args) {
        PrintStream out = System.out;
        out.printf(message, args);
    }

    private void visitRoot(final RootNode root, final AbstractNodeVisitor visitor) {
        if (TRACE) {
            trace("BEGIN: Visit root %s for %s%n", root.toString(), visitor);
        }

        visitor.root = root;
        visitor.providedTags = getProvidedTags(root);

        if (visitor.shouldVisit()) {
            if (TRACE) {
                trace("BEGIN: Traverse root %s for %s%n", root.toString(), visitor);
            }
            root.accept(visitor);
            if (TRACE) {
                trace("END: Traverse root %s for %s%n", root.toString(), visitor);
            }
        }
        if (TRACE) {
            trace("END: Visited root %s for %s%n", root.toString(), visitor);
        }
    }

    static void removeWrapper(ProbeNode node) {
        if (TRACE) {
            trace("Remove wrapper for %s%n", node.getContext().getInstrumentedSourceSection());
        }
        WrapperNode wrapperNode = node.findWrapper();
        ((Node) wrapperNode).replace(wrapperNode.getDelegateNode());
    }

    private static void invalidateWrapper(Node node) {
        Node parent = node.getParent();
        if (!(parent instanceof WrapperNode)) {
            // not yet wrapped
            return;
        }
        invalidateWrapperImpl((WrapperNode) parent, node);
    }

    private static void invalidateWrapperImpl(WrapperNode parent, Node node) {
        ProbeNode probeNode = parent.getProbeNode();
        if (TRACE) {
            SourceSection section = probeNode.getContext().getInstrumentedSourceSection();
            trace("Invalidate wrapper for %s, section %s %n", node, section);
        }
        if (probeNode != null) {
            probeNode.invalidate();
        }
    }

    static boolean hasTagImpl(Set<Class<?>> providedTags, Node node, Class<?> tag) {
        if (providedTags.contains(tag)) {
            return AccessorInstrumentHandler.nodesAccess().isTaggedWith(node, tag);
        }
        return false;
    }

    static Instrumentable getInstrumentable(Node node) {
        Instrumentable instrumentable = node.getClass().getAnnotation(Instrumentable.class);
        if (instrumentable != null && !(node instanceof WrapperNode)) {
            return instrumentable;
        }
        return null;
    }

    private <T> T lookup(Object key, Class<T> type) {
        AbstractInstrumenter value = instrumenterMap.get(key);
        return value == null ? null : value.lookup(this, type);
    }

    private abstract static class AbstractNodeVisitor implements NodeVisitor {

        RootNode root;
        Set<Class<?>> providedTags;

        abstract boolean shouldVisit();

    }

    private abstract class AbstractBindingVisitor extends AbstractNodeVisitor {

        protected final EventBinding<?> binding;

        AbstractBindingVisitor(EventBinding<?> binding) {
            this.binding = binding;
        }

        @Override
        boolean shouldVisit() {
            return binding.isInstrumentedRoot(providedTags, root, root.getSourceSection());
        }

        public final boolean visit(Node node) {
            SourceSection sourceSection = node.getSourceSection();
            if (isInstrumentableNode(node, sourceSection)) {
                if (binding.isInstrumentedLeaf(providedTags, node, sourceSection)) {
                    if (TRACE) {
                        traceFilterCheck("hit", providedTags, binding, node, sourceSection);
                    }
                    visitInstrumented(node, sourceSection);
                } else {
                    if (TRACE) {
                        traceFilterCheck("miss", providedTags, binding, node, sourceSection);
                    }
                }
            }
            return true;
        }

        protected abstract void visitInstrumented(Node node, SourceSection section);
    }

    private static void traceFilterCheck(String result, Set<Class<?>> providedTags, EventBinding<?> binding, Node node, SourceSection sourceSection) {
        Set<Class<?>> tags = binding.getFilter().getReferencedTags();
        Set<Class<?>> containedTags = new HashSet<>();
        for (Class<?> tag : tags) {
            if (hasTagImpl(providedTags, node, tag)) {
                containedTags.add(tag);
            }
        }
        trace("  Filter %4s %s section:%s tags:%s%n", result, binding.getFilter(), sourceSection, containedTags);
    }

    private abstract class AbstractBindingsVisitor extends AbstractNodeVisitor {

        private final EventBindingList bindings;
        private final boolean visitForEachBinding;

        AbstractBindingsVisitor(EventBindingList bindings, boolean visitForEachBinding) {
            this.bindings = bindings;
            this.visitForEachBinding = visitForEachBinding;
        }

        @Override
        boolean shouldVisit() {
            if (bindings.isEmpty()) {
                return false;
            }
            final RootNode localRoot = root;
            if (localRoot == null) {
                return false;
            }
            SourceSection sourceSection = localRoot.getSourceSection();

            // no locking required for the atomic reference arrays
            AtomicReferenceArray<EventBinding<?>> bindingsArray = bindings.getArray();
            for (int i = 0; i < bindingsArray.length(); i++) {
                EventBinding<?> binding = bindingsArray.get(i);
                if (binding == null) {
                    break;
                } else if (binding.isDisposed()) {
                    continue;
                }

                if (binding.isInstrumentedRoot(providedTags, localRoot, sourceSection)) {
                    return true;
                }
            }
            return false;
        }

        public final boolean visit(Node node) {
            SourceSection sourceSection = node.getSourceSection();
            if (isInstrumentableNode(node, sourceSection)) {
                // no locking required for these atomic reference arrays
                AtomicReferenceArray<EventBinding<?>> bindingsArray = bindings.getArray();
                for (int i = 0; i < bindingsArray.length(); i++) {
                    EventBinding<?> binding = bindingsArray.get(i);
                    if (binding == null) {
                        break;
                    } else if (binding.isDisposed()) {
                        continue;
                    }
                    if (binding.isInstrumentedFull(providedTags, root, node, sourceSection)) {
                        if (TRACE) {
                            traceFilterCheck("hit", providedTags, binding, node, sourceSection);
                        }
                        visitInstrumented(binding, node, sourceSection);
                        if (!visitForEachBinding) {
                            break;
                        }
                    } else {
                        if (TRACE) {
                            traceFilterCheck("miss", providedTags, binding, node, sourceSection);
                        }
                    }

                }
            }
            return true;
        }

        protected abstract void visitInstrumented(EventBinding<?> binding, Node node, SourceSection section);

    }

    /* Insert wrappers for a single bindings. */
    private final class InsertWrappersWithBindingVisitor extends AbstractBindingVisitor {

        InsertWrappersWithBindingVisitor(EventBinding<?> filter) {
            super(filter);
        }

        @Override
        protected void visitInstrumented(Node node, SourceSection section) {
            insertWrapper(node, section);
        }

    }

    private final class DisposeWrappersVisitor extends AbstractBindingVisitor {

        DisposeWrappersVisitor(EventBinding<?> binding) {
            super(binding);
        }

        @Override
        protected void visitInstrumented(Node node, SourceSection section) {
            invalidateWrapper(node);
        }
    }

    private final class InsertWrappersVisitor extends AbstractBindingsVisitor {

        InsertWrappersVisitor(EventBindingList bindings) {
            super(bindings, false);
        }

        @Override
        protected void visitInstrumented(EventBinding<?> binding, Node node, SourceSection section) {
            insertWrapper(node, section);
        }
    }

    private final class DisposeWrappersWithBindingVisitor extends AbstractBindingsVisitor {

        DisposeWrappersWithBindingVisitor(EventBindingList bindings) {
            super(bindings, false);
        }

        @Override
        protected void visitInstrumented(EventBinding<?> binding, Node node, SourceSection section) {
            invalidateWrapper(node);
        }

    }

    private final class NotifyLoadedWithBindingVisitor extends AbstractBindingVisitor {

        NotifyLoadedWithBindingVisitor(EventBinding<?> binding) {
            super(binding);
        }

        @Override
        protected void visitInstrumented(Node node, SourceSection section) {
            notifySourceSectionLoaded(binding, node, section);
        }

    }

    private final class NotifyLoadedListenerVisitor extends AbstractBindingsVisitor {

        NotifyLoadedListenerVisitor(EventBindingList bindings) {
            super(bindings, true);
        }

        @Override
        protected void visitInstrumented(EventBinding<?> binding, Node node, SourceSection section) {
            notifySourceSectionLoaded(binding, node, section);
        }
    }

    /**
     * Provider of instrumentation services for {@linkplain TruffleInstrument external clients} of
     * instrumentation.
     */
    final class InstrumentClientInstrumenter extends AbstractInstrumenter {

        private final Class<?> instrumentClass;
        private Object[] services;
        private TruffleInstrument instrument;
        private final Env env;

        InstrumentClientInstrumenter(Class<?> instrumentClass, OutputStream out, OutputStream err, InputStream in) {
            this.instrumentClass = instrumentClass;
            this.env = new Env(this, out, err, in);
        }

        @Override
        boolean isInstrumentableRoot(RootNode rootNode) {
            return true;
        }

        @Override
        public Set<Class<?>> queryTags(Node node) {
            return queryTagsImpl(node, null);
        }

        @Override
        void verifyFilter(SourceSectionFilter filter) {
        }

        Class<?> getInstrumentClass() {
            return instrumentClass;
        }

        Env getEnv() {
            return env;
        }

        @Override
        void initialize() {
            if (TRACE) {
                trace("Initialize instrument %s class %s %n", instrument, instrumentClass);
            }
            assert instrument == null;
            try {
                this.instrument = (TruffleInstrument) instrumentClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                failInstrumentInitialization(String.format("Failed to create new instrumenter class %s", instrumentClass.getName()), e);
                return;
            }
            try {
                services = env.onCreate(instrument);
            } catch (Throwable e) {
                failInstrumentInitialization(String.format("Failed calling onCreate of instrument class %s", instrumentClass.getName()), e);
                return;
            }
            if (TRACE) {
                trace("Initialized instrument %s class %s %n", instrument, instrumentClass);
            }
        }

        private void failInstrumentInitialization(String message, Throwable t) {
            Exception exception = new Exception(message, t);
            PrintStream stream = new PrintStream(env.err());
            exception.printStackTrace(stream);
        }

        boolean isInitialized() {
            return instrument != null;
        }

        TruffleInstrument getInstrument() {
            return instrument;
        }

        @Override
        void dispose() {
            if (isInitialized()) {
                instrument.onDispose(env);
            }
        }

        @Override
        <T> T lookup(InstrumentationHandler handler, Class<T> type) {
            if (instrument == null) {
                handler.initializeInstrumentation();
            }
            if (services != null) {
                for (Object service : services) {
                    if (type.isInstance(service)) {
                        return type.cast(service);
                    }
                }
            }
            return null;
        }

    }

    /**
     * Provider of instrumentation services for {@linkplain TruffleLanguage language
     * implementations}.
     */
    final class LanguageClientInstrumenter<T> extends AbstractInstrumenter {

        @SuppressWarnings("unused") private final TruffleLanguage.Env env;
        private final TruffleLanguage<T> language;

        LanguageClientInstrumenter(TruffleLanguage<T> language, TruffleLanguage.Env env) {
            this.language = language;
            this.env = env;
        }

        @Override
        boolean isInstrumentableRoot(RootNode node) {
            if (AccessorInstrumentHandler.nodesAccess().findLanguage(node.getRootNode()) != language.getClass()) {
                return false;
            }
            // TODO (chumer) check for the context instance
            return true;
        }

        @Override
        public Set<Class<?>> queryTags(Node node) {
            return queryTagsImpl(node, language.getClass());
        }

        @Override
        void verifyFilter(SourceSectionFilter filter) {
            Set<Class<?>> providedTags = getProvidedTags(language.getClass());
            // filters must not reference tags not declared in @RequiredTags
            Set<Class<?>> referencedTags = filter.getReferencedTags();
            if (!providedTags.containsAll(referencedTags)) {
                Set<Class<?>> missingTags = new HashSet<>(referencedTags);
                missingTags.removeAll(providedTags);
                Set<Class<?>> allTags = new LinkedHashSet<>(providedTags);
                allTags.addAll(missingTags);
                StringBuilder builder = new StringBuilder("{");
                String sep = "";
                for (Class<?> tag : allTags) {
                    builder.append(sep);
                    builder.append(tag.getSimpleName());
                    sep = ", ";
                }
                builder.append("}");

                throw new IllegalArgumentException(String.format("The attached filter %s references the following tags %s which are not declared as provided by the language. " +
                                "To fix this annotate the language class %s with @%s(%s).",
                                filter, missingTags, language.getClass().getName(), ProvidedTags.class.getSimpleName(), builder));
            }
        }

        @Override
        void initialize() {
            // nothing to do
        }

        @Override
        void dispose() {
            // nothing to do
        }

        @Override
        <S> S lookup(InstrumentationHandler handler, Class<S> type) {
            return null;
        }

    }

    /**
     * Shared implementation of instrumentation services for clients whose requirements and
     * privileges may vary.
     */
    abstract class AbstractInstrumenter extends Instrumenter {

        abstract void initialize();

        abstract void dispose();

        abstract <T> T lookup(InstrumentationHandler handler, Class<T> type);

        void disposeBinding(EventBinding<?> binding) {
            InstrumentationHandler.this.disposeBinding(binding);
        }

        abstract boolean isInstrumentableRoot(RootNode rootNode);

        final Set<Class<?>> queryTagsImpl(Node node, Class<?> onlyLanguage) {
            SourceSection sourceSection = node.getSourceSection();
            if (!InstrumentationHandler.isInstrumentableNode(node, sourceSection)) {
                return Collections.emptySet();
            }

            RootNode root = node.getRootNode();
            if (root == null) {
                return Collections.emptySet();
            }

            Class<?> language = AccessorInstrumentHandler.nodesAccess().findLanguage(root);
            if (onlyLanguage != null && language != onlyLanguage) {
                throw new IllegalArgumentException("The language instrumenter cannot query tags of nodes of other languages.");
            }
            Set<Class<?>> providedTags = getProvidedTags(root);
            if (providedTags.isEmpty()) {
                return Collections.emptySet();
            }

            Set<Class<?>> tags = new HashSet<>();
            for (Class<?> providedTag : providedTags) {
                if (hasTagImpl(providedTags, node, providedTag)) {
                    tags.add(providedTag);
                }
            }
            return Collections.unmodifiableSet(tags);
        }

        @Override
        public final <T extends ExecutionEventNodeFactory> EventBinding<T> attachFactory(SourceSectionFilter filter, T factory) {
            verifyFilter(filter);
            return InstrumentationHandler.this.attachFactory(this, filter, factory);
        }

        @Override
        public final <T extends ExecutionEventListener> EventBinding<T> attachListener(SourceSectionFilter filter, T listener) {
            verifyFilter(filter);
            return InstrumentationHandler.this.attachListener(this, filter, listener);
        }

        @Override
        public <T extends LoadSourceEventListener> EventBinding<T> attachLoadSourceListener(SourceSectionFilter filter, T listener, boolean notifyLoaded) {
            verifySourceOnly(filter);
            verifyFilter(filter);
            return InstrumentationHandler.this.attachSourceListener(this, filter, listener, notifyLoaded);
        }

        @Override
        public <T extends LoadSourceSectionEventListener> EventBinding<T> attachLoadSourceSectionListener(SourceSectionFilter filter, T listener, boolean notifyLoaded) {
            verifyFilter(filter);
            return InstrumentationHandler.this.attachSourceSectionListener(this, filter, listener, notifyLoaded);
        }

        private void verifySourceOnly(SourceSectionFilter filter) {
            if (!filter.isSourceOnly()) {
                throw new IllegalArgumentException(String.format("The attached filter %s uses filters that require source sections to verifiy. " +
                                "Source listeners can only use filter critera based on Source objects like mimeTypeIs or sourceIs.", filter));
            }
        }

        abstract void verifyFilter(SourceSectionFilter filter);

    }

    /**
     * A list data structure that is optimized for fast non-blocking traversals. There is no
     * explicit removal. Removals are based on a side effect of the element. Adds do block,
     * automatically compact and perform cleanups whenever they were scheduled.
     */
    private abstract static class AbstractAsncList<T> {
        /*
         * We use an atomic reference list as we don't want to see wholes in the array when
         * appending to it. This allows us to use null as a safe terminator for the array.
         */
        private volatile AtomicReferenceArray<T> values;

        /*
         * Size can be non volatile as its not exposed or used for traversal.
         */
        private int size;

        AbstractAsncList(int initialCapacity) {
            if (initialCapacity <= 0) {
                throw new IllegalArgumentException("Invalid initial capacity " + initialCapacity);
            }
            this.values = new AtomicReferenceArray<>(initialCapacity);
        }

        public final synchronized void add(T reference) {
            if (reference == null) {
                // fail early
                throw new NullPointerException();
            }
            if (size >= values.length()) {
                compact();
            }
            values.set(size++, reference);
        }

        public final boolean isEmpty() {
            return values.get(0) == null;
        }

        protected abstract boolean isAlive(T element);

        private void compact() {
            AtomicReferenceArray<T> localValues = values;
            int liveElements = 0;
            /*
             * We count the still alive elements.
             */
            for (int i = 0; i < localValues.length(); i++) {
                T ref = localValues.get(i);
                if (ref == null) {
                    break;
                }
                if (isAlive(ref)) {
                    liveElements++;
                }
            }

            /*
             * We ensure that the capacity after compaction is always twice as big as the number of
             * live elements. This might either to a growing or shrinking array.
             */
            AtomicReferenceArray<T> newValues = new AtomicReferenceArray<>(Math.max(liveElements * 2, 8));
            int index = 0;
            for (int i = 0; i < localValues.length(); i++) {
                T ref = localValues.get(i);
                if (ref == null) {
                    break;
                }
                if (isAlive(ref)) {
                    newValues.set(index++, ref);
                }
            }

            this.size = index;
            this.values = newValues;

        }

        /**
         * Returns an array which can be traversed without a lock. A null element in the list
         * indicates the end of the list. Non alive elements might be returned by this list as well
         * and need to be checked during traversal.
         */
        public final AtomicReferenceArray<T> getArray() {
            return values;
        }

    }

    /**
     * A async list implementation that removes elements whenever a binding was disposed.
     */
    private static final class EventBindingList extends AbstractAsncList<EventBinding<?>> {

        public static final EventBindingList EMPTY = new EventBindingList(1);

        EventBindingList(int initialCapacity) {
            super(initialCapacity);
        }

        @Override
        protected boolean isAlive(EventBinding<?> element) {
            return !element.isDisposed();
        }
    }

    /**
     * An async list using weak references.
     */
    private static final class WeakAsyncList<T> extends AbstractAsncList<WeakReference<T>> {

        WeakAsyncList(int initialCapacity) {
            super(initialCapacity);
        }

        @Override
        protected boolean isAlive(WeakReference<T> element) {
            return element.get() != null;
        }

    }

    static final AccessorInstrumentHandler ACCESSOR = new AccessorInstrumentHandler();

    static final class AccessorInstrumentHandler extends Accessor {

        static Accessor.Nodes nodesAccess() {
            return ACCESSOR.nodes();
        }

        static Accessor.LanguageSupport langAccess() {
            return ACCESSOR.languageSupport();
        }

        static Accessor.EngineSupport engineAccess() {
            return ACCESSOR.engineSupport();
        }

        @SuppressWarnings("rawtypes")
        protected CallTarget parse(Class<? extends TruffleLanguage> languageClass, Source code, Node context, String... argumentNames) throws IOException {
            final TruffleLanguage<?> truffleLanguage = engineSupport().findLanguageImpl(null, languageClass, code.getMimeType());
            return langAccess().parse(truffleLanguage, code, context, argumentNames);
        }

        @Override
        protected InstrumentSupport instrumentSupport() {
            return new InstrumentImpl();
        }

        static final class InstrumentImpl extends InstrumentSupport {

            @Override
            public Object createInstrumentationHandler(Object vm, OutputStream out, OutputStream err, InputStream in) {
                return new InstrumentationHandler(out, err, in);
            }

            @Override
            public void addInstrument(Object instrumentationHandler, Object key, Class<?> instrumentClass) {
                ((InstrumentationHandler) instrumentationHandler).addInstrument(key, instrumentClass);
            }

            @Override
            public void disposeInstrument(Object instrumentationHandler, Object key, boolean cleanupRequired) {
                ((InstrumentationHandler) instrumentationHandler).disposeInstrumenter(key, cleanupRequired);
            }

            @Override
            public void collectEnvServices(Set<Object> collectTo, Object vm, TruffleLanguage<?> impl, TruffleLanguage.Env env) {
                InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(vm);
                Instrumenter instrumenter = instrumentationHandler.forLanguage(env, impl);
                collectTo.add(instrumenter);
            }

            @Override
            public <T> T getInstrumentationHandlerService(Object vm, Object key, Class<T> type) {
                InstrumentationHandler instrumentationHandler = (InstrumentationHandler) vm;
                return instrumentationHandler.lookup(key, type);
            }

            @Override
            public void detachLanguageFromInstrumentation(Object vm, com.oracle.truffle.api.TruffleLanguage.Env env) {
                InstrumentationHandler instrumentationHandler = (InstrumentationHandler) engineAccess().getInstrumentationHandler(vm);
                instrumentationHandler.detachLanguage(langAccess().findContext(env));
            }

            @Override
            public void onFirstExecution(RootNode rootNode) {
                Object instrumentationHandler = engineAccess().getInstrumentationHandler(null);
                // we want to still support cases where call targets are executed without an
                // enclosing
                // engine.
                if (instrumentationHandler != null) {
                    ((InstrumentationHandler) instrumentationHandler).onFirstExecution(rootNode);
                }
            }

            @Override
            public void onLoad(RootNode rootNode) {
                Object instrumentationHandler = engineAccess().getInstrumentationHandler(null);
                // we want to still support cases where call targets are executed without an
                // enclosing
                // engine.
                if (instrumentationHandler != null) {
                    ((InstrumentationHandler) instrumentationHandler).onLoad(rootNode);
                }
            }
        }
    }

}
