/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor.NullFunction;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.interop.LLVMTypedForeignObject;
import com.oracle.truffle.llvm.runtime.interop.export.InteropNodeFactory;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMStack.StackPointer;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.pointer.LLVMManagedPointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMContext {
    private final List<Path> libraryPaths = new ArrayList<>();
    private final List<ExternalLibrary> externalLibraries = new ArrayList<>();

    private DataLayout dataLayout;

    private final List<LLVMThread> runningThreads = new ArrayList<>();
    private final LLVMThreadingStack threadingStack;
    private final Object[] mainArguments;
    private final Map<String, String> environment;
    private final LinkedList<LLVMNativePointer> caughtExceptionStack = new LinkedList<>();
    private final HashMap<String, Integer> nativeCallStatistics;
    private final Object handlesLock;
    private final IdentityHashMap<TruffleObject, LLVMNativePointer> toNative;
    private final HashMap<LLVMNativePointer, TruffleObject> toManaged;
    private final LLVMSourceContext sourceContext;
    private final LLVMGlobalsStack globalStack;

    private final Env env;
    private final LLVMScope globalScope;
    private final DynamicLinkChain dynamicLinkChain;
    private final List<RootCallTarget> destructorFunctions;
    private final LLVMFunctionPointerRegistry functionPointerRegistry;

    private final List<ContextExtension> contextExtensions;

    private final MaterializedFrame globalFrame = Truffle.getRuntime().createMaterializedFrame(new Object[0]);
    private final FrameDescriptor globalFrameDescriptor = globalFrame.getFrameDescriptor();

    // we are not able to clean up ThreadLocals properly, so we are using maps instead
    private final Map<Thread, Object> tls = new HashMap<>();
    private final Map<Thread, LLVMNativePointer> clearChildTid = new HashMap<>();

    // signals
    private final LLVMNativePointer sigDfl;
    private final LLVMNativePointer sigIgn;
    private final LLVMNativePointer sigErr;

    private boolean initialized;
    private boolean cleanupNecessary;
    private boolean defaultLibrariesLoaded;

    private final InteropNodeFactory interopNodeFactory;

    public static final class LLVMGlobalsStack {
        private static final int ALIGNMENT = 8;
        private static final int SIZE = 10 * 1024 * 1024;
        private static final int LARGE_ALLOC = 512 * 1024;

        private final LLVMMemory memory;
        private final long lowerBounds;
        private final long upperBounds;

        private long[] overflowSlots;
        private int nextFreeOverflowSlot;
        private long stackPointer;

        @SuppressWarnings("deprecation")
        public LLVMGlobalsStack() {
            this.memory = LLVMMemory.getInstance();
            long stackAllocation = memory.allocateMemory(SIZE).asNative();
            this.lowerBounds = stackAllocation;
            this.upperBounds = stackAllocation + SIZE;
            this.stackPointer = upperBounds;
            this.nextFreeOverflowSlot = 0;
            this.overflowSlots = null;
        }

        @TruffleBoundary
        public synchronized void free() {
            memory.free(lowerBounds);
            for (int i = 0; i < nextFreeOverflowSlot; i++) {
                memory.free(overflowSlots[i]);
            }
        }

        @TruffleBoundary
        public synchronized long allocateStackMemory(final long size) {
            assert size >= 0;
            if (size < LARGE_ALLOC) {
                final long alignedAllocation = (stackPointer - size) & -ALIGNMENT;
                assert alignedAllocation <= stackPointer;
                if (alignedAllocation >= lowerBounds) {
                    stackPointer = alignedAllocation;
                    return alignedAllocation;
                }
            }

            return overflowAlloc(size);
        }

        private long overflowAlloc(final long size) {
            if (overflowSlots == null) {
                overflowSlots = new long[8];
            } else if (nextFreeOverflowSlot >= overflowSlots.length) {
                overflowSlots = Arrays.copyOf(overflowSlots, 2 * overflowSlots.length);
            }
            long allocation = memory.allocateMemory(size).asNative();
            overflowSlots[nextFreeOverflowSlot++] = allocation;
            return allocation;
        }
    }

    private final class LLVMFunctionPointerRegistry {
        private int currentFunctionIndex = 0;
        private final HashMap<LLVMNativePointer, LLVMFunctionDescriptor> functionDescriptors = new HashMap<>();

        synchronized LLVMFunctionDescriptor getDescriptor(LLVMNativePointer pointer) {
            return functionDescriptors.get(pointer);
        }

        synchronized void register(LLVMNativePointer pointer, LLVMFunctionDescriptor desc) {
            functionDescriptors.put(pointer, desc);
        }

        synchronized LLVMFunctionDescriptor create(String name, FunctionType type) {
            LLVMFunctionDescriptor fn = LLVMFunctionDescriptor.createDescriptor(LLVMContext.this, name, type, currentFunctionIndex++);
            if (fn.isNullFunction()) {
                assert !functionDescriptors.containsKey(LLVMNativePointer.createNull());
                functionDescriptors.put(LLVMNativePointer.createNull(), fn);
            }
            return fn;
        }
    }

    public LLVMContext(Env env, List<ContextExtension> contextExtensions, InteropNodeFactory interopNodeFactory) {
        this.env = env;
        this.contextExtensions = contextExtensions;
        this.initialized = false;
        this.cleanupNecessary = false;
        this.defaultLibrariesLoaded = false;

        this.dataLayout = new DataLayout();
        this.destructorFunctions = new ArrayList<>();
        this.globalStack = new LLVMGlobalsStack();
        this.nativeCallStatistics = SulongEngineOption.isTrue(env.getOptions().get(SulongEngineOption.NATIVE_CALL_STATS)) ? new HashMap<>() : null;
        this.threadingStack = new LLVMThreadingStack(Thread.currentThread(), env.getOptions().get(SulongEngineOption.STACK_SIZE_KB));
        this.sigDfl = LLVMNativePointer.create(0);
        this.sigIgn = LLVMNativePointer.create(1);
        this.sigErr = LLVMNativePointer.create(-1);
        this.toNative = new IdentityHashMap<>();
        this.toManaged = new HashMap<>();
        this.handlesLock = new Object();
        this.functionPointerRegistry = new LLVMFunctionPointerRegistry();
        this.sourceContext = new LLVMSourceContext();

        this.globalScope = createGlobalScope();
        this.dynamicLinkChain = new DynamicLinkChain();

        Object mainArgs = env.getConfig().get(LLVMLanguage.MAIN_ARGS_KEY);
        this.mainArguments = mainArgs == null ? env.getApplicationArguments() : (Object[]) mainArgs;
        this.environment = System.getenv();

        this.interopNodeFactory = interopNodeFactory;

        addLibraryPaths(SulongEngineOption.getPolyglotOptionSearchPaths(env));
    }

    private LLVMScope createGlobalScope() {
        LLVMScope scope = new LLVMScope();
        LLVMFunctionDescriptor nullFunction = scope.functions().getOrCreate(this, "<nullFunction>", new FunctionType(MetaType.UNKNOWN, new Type[0], false));
        nullFunction.define(new ExternalLibrary("Default", false, false), new NullFunction());
        return scope;
    }

    public void initialize() {
        // we can't do the initialization in the LLVMContext constructor nor in
        // Sulong.createContext() because Truffle is not properly initialized there. So, we need to
        // do it in a delayed way.
        if (!initialized) {
            assert !cleanupNecessary && globalScope.functions().contains("@__sulong_init_context");
            initialized = true;
            cleanupNecessary = true;

            LLVMFunctionDescriptor initContextDescriptor = globalScope.functions().get("@__sulong_init_context");
            RootCallTarget initContextFunction = initContextDescriptor.getLLVMIRFunction();
            try (StackPointer stackPointer = threadingStack.getStack().newFrame()) {
                Object[] args = new Object[]{stackPointer, toTruffleObjects(getApplicationArguments()), toTruffleObjects(getEnvironmentVariables())};
                initContextFunction.call(args);
            }
        }
    }

    public boolean areDefaultLibrariesLoaded() {
        return defaultLibrariesLoaded;
    }

    public void setDefaultLibrariesLoaded() {
        defaultLibrariesLoaded = true;
    }

    private String[] getApplicationArguments() {
        int mainArgsCount = mainArguments == null ? 0 : mainArguments.length;
        String[] result = new String[mainArgsCount + 1];
        // we don't have an application path at this point in time. it will be overwritten when
        // _start is called
        result[0] = "";
        for (int i = 1; i < result.length; i++) {
            result[i] = mainArguments[i - 1].toString();
        }
        return result;
    }

    private String[] getEnvironmentVariables() {
        return environment.entrySet().stream().map((e) -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }

    private LLVMManagedPointer toTruffleObjects(String[] values) {
        TruffleObject[] result = new TruffleObject[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (TruffleObject) env.asGuestValue(values[i].getBytes());
        }
        return LLVMManagedPointer.create(LLVMTypedForeignObject.createUnknown((TruffleObject) env.asGuestValue(result)));
    }

    public void dispose(LLVMMemory memory) {
        printNativeCallStatistic();

        // the following cases exist for cleanup:
        // - exit() or interop: execute all atexit functions, shutdown stdlib, flush IO, and execute
        // destructors
        // - _exit(), _Exit(), or abort(): no cleanup necessary
        if (cleanupNecessary) {
            try {
                RootCallTarget disposeContext = globalScope.functions().get("@__sulong_dispose_context").getLLVMIRFunction();
                try (StackPointer stackPointer = threadingStack.getStack().newFrame()) {
                    disposeContext.call(stackPointer);
                }
            } catch (ControlFlowException e) {
                // nothing needs to be done as the behavior is not defined
            }
        }

        threadingStack.freeMainStack(memory);
        globalStack.free();
    }

    public LLVMGlobalsStack getGlobalsStack() {
        return globalStack;
    }

    public InteropNodeFactory getInteropNodeFactory() {
        return interopNodeFactory;
    }

    public <T> T getContextExtension(Class<T> type) {
        T result = getContextExtensionOrNull(type);
        if (result != null) {
            return result;
        }
        throw new IllegalStateException("No context extension for: " + type);
    }

    public <T> T getContextExtensionOrNull(Class<T> type) {
        CompilerAsserts.neverPartOfCompilation();
        for (ContextExtension ce : contextExtensions) {
            if (ce.extensionClass() == type) {
                return type.cast(ce);
            }
        }
        return null;
    }

    public boolean hasContextExtension(Class<?> type) {
        return getContextExtensionOrNull(type) != null;
    }

    public int getByteAlignment(Type type) {
        return type.getAlignment(dataLayout);
    }

    public int getByteSize(Type type) {
        return type.getSize(dataLayout);
    }

    public int getBytePadding(long offset, Type type) {
        return Type.getPadding(offset, type, dataLayout);
    }

    public long getIndexOffset(long index, AggregateType type) {
        return type.getOffsetOf(index, dataLayout);
    }

    public DataLayout getDataSpecConverter() {
        return dataLayout;
    }

    public ExternalLibrary addExternalLibrary(String lib, boolean isNative, boolean renameConflictingSymbols) {
        CompilerAsserts.neverPartOfCompilation();
        Path path = locateExternalLibrary(lib);
        ExternalLibrary externalLib = new ExternalLibrary(path, isNative, renameConflictingSymbols);
        int index = externalLibraries.indexOf(externalLib);
        if (index < 0) {
            externalLibraries.add(externalLib);
            return externalLib;
        }
        return null;
    }

    public List<ExternalLibrary> getExternalLibraries(Predicate<ExternalLibrary> filter) {
        return externalLibraries.stream().filter(f -> filter.test(f)).collect(Collectors.toList());
    }

    public void addLibraryPaths(List<String> paths) {
        for (String p : paths) {
            addLibraryPath(p);
        }
    }

    private void addLibraryPath(String p) {
        Path path = Paths.get(p);
        File file = path.toFile();
        if (file.isDirectory()) {
            if (!libraryPaths.contains(path)) {
                libraryPaths.add(path);
            }
        }

        // TODO (chaeubl): we should throw an exception in this case but this will cause gate
        // failures at the moment, because the library path is not always set correctly
    }

    @TruffleBoundary
    private Path locateExternalLibrary(String lib) {
        Path libPath = Paths.get(lib);
        if (libPath.isAbsolute()) {
            if (libPath.toFile().exists()) {
                return libPath;
            } else {
                throw new LinkageError(String.format("Library \"%s\" does not exist.", lib));
            }
        }

        for (Path p : libraryPaths) {
            Path absPath = Paths.get(p.toString(), lib);
            if (absPath.toFile().exists()) {
                return absPath;
            }
        }

        return libPath;
    }

    public Env getEnv() {
        return env;
    }

    public LLVMScope getGlobalScope() {
        return globalScope;
    }

    @TruffleBoundary
    public Object getThreadLocalStorage() {
        Object value = tls.get(Thread.currentThread());
        if (value != null) {
            return value;
        }
        return LLVMNativePointer.createNull();
    }

    @TruffleBoundary
    public void setThreadLocalStorage(Object value) {
        tls.put(Thread.currentThread(), value);
    }

    @TruffleBoundary
    public LLVMNativePointer getClearChildTid() {
        LLVMNativePointer value = clearChildTid.get(Thread.currentThread());
        if (value != null) {
            return value;
        }
        return LLVMNativePointer.createNull();
    }

    @TruffleBoundary
    public void setClearChildTid(LLVMNativePointer value) {
        clearChildTid.put(Thread.currentThread(), value);
    }

    @TruffleBoundary
    public LLVMFunctionDescriptor getFunctionDescriptor(LLVMNativePointer handle) {
        return functionPointerRegistry.getDescriptor(handle);
    }

    @TruffleBoundary
    public LLVMFunctionDescriptor createFunctionDescriptor(String name, FunctionType type) {
        return functionPointerRegistry.create(name, type);
    }

    @TruffleBoundary
    public void registerFunctionPointer(LLVMNativePointer address, LLVMFunctionDescriptor descriptor) {
        functionPointerRegistry.register(address, descriptor);
    }

    public LLVMNativePointer getSigDfl() {
        return sigDfl;
    }

    public LLVMNativePointer getSigIgn() {
        return sigIgn;
    }

    public LLVMNativePointer getSigErr() {
        return sigErr;
    }

    @TruffleBoundary
    public boolean isHandle(LLVMNativePointer address) {
        synchronized (handlesLock) {
            return toManaged.containsKey(address);
        }
    }

    @TruffleBoundary
    public TruffleObject getManagedObjectForHandle(LLVMNativePointer address) {
        synchronized (handlesLock) {
            final TruffleObject object = toManaged.get(address);

            if (object == null) {
                throw new UnsupportedOperationException("Cannot resolve native handle: " + address);
            }

            return object;
        }
    }

    private static TruffleObject getIdentityKey(TruffleObject obj) {
        if (obj instanceof LLVMTypedForeignObject) {
            return ((LLVMTypedForeignObject) obj).getForeign();
        } else {
            return obj;
        }
    }

    @TruffleBoundary
    public void releaseHandle(LLVMMemory memory, LLVMNativePointer address) {
        synchronized (handlesLock) {
            final TruffleObject object = toManaged.get(address);

            if (object == null) {
                throw new UnsupportedOperationException("Cannot resolve native handle: " + address);
            }

            toManaged.remove(address);
            toNative.remove(getIdentityKey(object));
            memory.free(address);
        }
    }

    @TruffleBoundary
    public LLVMNativePointer getHandleForManagedObject(LLVMMemory memory, TruffleObject object) {
        synchronized (handlesLock) {
            return toNative.computeIfAbsent(getIdentityKey(object), (k) -> {
                LLVMNativePointer allocatedMemory = memory.allocateMemory(Long.BYTES);
                memory.putI64(allocatedMemory, 0xdeadbeef);
                toManaged.put(allocatedMemory, object);
                return allocatedMemory;
            });
        }
    }

    @TruffleBoundary
    public LLVMNativePointer getDerefHandleForManagedObject(LLVMMemory memory, TruffleObject object) {
        synchronized (handlesLock) {
            return toNative.computeIfAbsent(object, (k) -> {
                LLVMNativePointer allocatedMemory = memory.allocateDerefMemory();
                toManaged.put(allocatedMemory, object);
                return allocatedMemory;
            });
        }
    }

    @TruffleBoundary
    public void registerNativeCall(LLVMFunctionDescriptor descriptor) {
        if (nativeCallStatistics != null) {
            String name = descriptor.getName() + " " + descriptor.getType();
            if (nativeCallStatistics.containsKey(name)) {
                int count = nativeCallStatistics.get(name) + 1;
                nativeCallStatistics.put(name, count);
            } else {
                nativeCallStatistics.put(name, 1);
            }
        }
    }

    public LinkedList<LLVMNativePointer> getCaughtExceptionStack() {
        return caughtExceptionStack;
    }

    public LLVMThreadingStack getThreadingStack() {
        return threadingStack;
    }

    public void registerDestructorFunctions(RootCallTarget destructor) {
        assert destructor != null;
        assert !destructorFunctions.contains(destructor);
        destructorFunctions.add(destructor);
    }

    public void registerScope(LLVMScope scope) {
        dynamicLinkChain.addScope(scope);
    }

    public synchronized void registerThread(LLVMThread thread) {
        assert !runningThreads.contains(thread);
        runningThreads.add(thread);
    }

    public synchronized void unregisterThread(LLVMThread thread) {
        runningThreads.remove(thread);
        assert !runningThreads.contains(thread);
    }

    @TruffleBoundary
    public synchronized void shutdownThreads() {
        // we need to iterate over a copy of the list, because stop() can modify the original list
        for (LLVMThread node : new ArrayList<>(runningThreads)) {
            node.stop();
        }
    }

    @TruffleBoundary
    public synchronized void awaitThreadTermination() {
        shutdownThreads();

        while (!runningThreads.isEmpty()) {
            LLVMThread node = runningThreads.get(0);
            node.awaitFinish();
            assert !runningThreads.contains(node); // should be unregistered by LLVMThreadNode
        }
    }

    public RootCallTarget[] getDestructorFunctions() {
        return destructorFunctions.toArray(new RootCallTarget[destructorFunctions.size()]);
    }

    public synchronized List<LLVMThread> getRunningThreads() {
        return Collections.unmodifiableList(runningThreads);
    }

    public interface FunctionFactory {
        LLVMFunctionDescriptor create(int index);
    }

    public void addDataLayout(DataLayout layout) {
        this.dataLayout = this.dataLayout.merge(layout);
    }

    public LLVMSourceContext getSourceContext() {
        return sourceContext;
    }

    public MaterializedFrame getGlobalFrame() {
        return globalFrame;
    }

    public FrameSlot getGlobalFrameSlot(Object symbol, Type type) {
        FrameSlotKind kind;
        if (type instanceof PrimitiveType) {
            switch (((PrimitiveType) type).getPrimitiveKind()) {
                case DOUBLE:
                    kind = FrameSlotKind.Double;
                    break;
                case FLOAT:
                    kind = FrameSlotKind.Float;
                    break;
                case HALF:
                case I16:
                case I32:
                    kind = FrameSlotKind.Int;
                    break;
                case I1:
                    kind = FrameSlotKind.Boolean;
                    break;
                case I64:
                    kind = FrameSlotKind.Long;
                    break;
                case I8:
                    kind = FrameSlotKind.Byte;
                    break;
                default:
                    kind = FrameSlotKind.Object;
                    break;
            }
        } else {
            kind = FrameSlotKind.Object;
        }
        return globalFrameDescriptor.findOrAddFrameSlot(symbol, type, kind);
    }

    public void setCleanupNecessary(boolean value) {
        cleanupNecessary = value;
    }

    private void printNativeCallStatistic() {
        if (nativeCallStatistics != null) {
            LinkedHashMap<String, Integer> sorted = nativeCallStatistics.entrySet().stream().sorted(Map.Entry.comparingByValue()).collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new));
            for (String s : sorted.keySet()) {
                System.err.println(String.format("Function %s \t count: %d", s, sorted.get(s)));
            }
        }
    }

    public static class ExternalLibrary {
        private final String name;
        private final Path path;
        private final boolean renameConflictingSymbols;

        @CompilationFinal private boolean isNative;

        public ExternalLibrary(String name, boolean isNative, boolean renameConflictingSymbols) {
            this(name, null, isNative, renameConflictingSymbols);
        }

        public ExternalLibrary(Path path, boolean isNative, boolean renameConflictingSymbols) {
            this(extractName(path), path, isNative, renameConflictingSymbols);
        }

        private ExternalLibrary(String name, Path path, boolean isNative, boolean renameConflictingSymbols) {
            this.name = name;
            this.path = path;
            this.isNative = isNative;
            this.renameConflictingSymbols = renameConflictingSymbols;
        }

        public Path getPath() {
            return path;
        }

        public boolean isNative() {
            return isNative;
        }

        public void setIsNative(boolean isNative) {
            this.isNative = isNative;
        }

        public boolean renameConflictingSymbols() {
            return renameConflictingSymbols;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj instanceof ExternalLibrary) {
                ExternalLibrary other = (ExternalLibrary) obj;
                return name.equals(other.name) && Objects.equals(path, other.path);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ Objects.hashCode(path);
        }

        private static String extractName(Path path) {
            String nameWithExt = path.getFileName().toString();
            int lengthWithoutExt = nameWithExt.lastIndexOf(".");
            if (lengthWithoutExt > 0) {
                return nameWithExt.substring(0, lengthWithoutExt);
            }
            return nameWithExt;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder(name);
            if (path != null) {
                result.append(" (");
                result.append(path);
                result.append(")");
            }
            return result.toString();
        }
    }

    public static class DynamicLinkChain {
        private final ArrayList<LLVMScope> scopes;

        public DynamicLinkChain() {
            this.scopes = new ArrayList<>();
        }

        public void addScope(LLVMScope scope) {
            // TODO (chaeubl): we should have an assertion that the same scope is not contained
            // multiple times
            scopes.add(scope);
        }
    }
}
