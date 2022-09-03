/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.NativeLookup.UnsupportedNativeTypeException;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceContext;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayoutConverter.DataSpecConverterImpl;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeFunctions;
import com.oracle.truffle.llvm.runtime.memory.LLVMThreadingStack;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.AggregateType;
import com.oracle.truffle.llvm.runtime.types.DataSpecConverter;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class LLVMContext {

    private final List<Path> libraryPaths = new ArrayList<>();
    private final List<Path> bitcodeLibraries = new ArrayList<>();
    private DataSpecConverterImpl targetDataLayout;

    private final List<RootCallTarget> globalVarInits = new ArrayList<>();
    private final List<RootCallTarget> globalVarDeallocs = new ArrayList<>();
    private final List<RootCallTarget> constructorFunctions = new ArrayList<>();
    private final List<RootCallTarget> destructorFunctions = new ArrayList<>();
    private final Deque<LLVMFunctionDescriptor> atExitFunctions = new ArrayDeque<>();
    private final List<LLVMThread> runningThreads = new ArrayList<>();
    private final NativeLookup nativeLookup;
    private final LLVMNativeFunctions nativeFunctions;
    private final LLVMThreadingStack threadingStack;
    private Object[] mainArguments;
    private Source mainSourceFile;
    private boolean bcLibrariesLoaded;
    private NativeIntrinsicProvider nativeIntrinsicsFactory;
    private final LinkedList<LLVMAddress> caughtExceptionStack = new LinkedList<>();
    private final LinkedList<DestructorStackElement> destructorStack = new LinkedList<>();
    private final HashMap<String, Integer> nativeCallStatistics;
    private final Object handlesLock;
    private final IdentityHashMap<TruffleObject, LLVMAddress> toNative;
    private final HashMap<LLVMAddress, TruffleObject> toManaged;
    private final LLVMSourceContext sourceContext;

    private final Env env;
    private final LLVMScope globalScope;
    private final LLVMFunctionPointerRegistry functionPointerRegistry;

    private final Map<Class<?>, Object> contextExtension;

    // #define SIG_DFL ((__sighandler_t) 0) /* Default action. */
    private final LLVMFunction sigDfl;

    // # define SIG_IGN ((__sighandler_t) 1) /* Ignore signal. */
    private final LLVMFunction sigIgn;

    // #define SIG_ERR ((__sighandler_t) -1) /* Error return. */
    private final LLVMFunction sigErr;

    public static final class DestructorStackElement {
        private final LLVMFunctionDescriptor destructor;
        private final long thiz;

        public DestructorStackElement(LLVMFunctionDescriptor destructor, LLVMAddress thiz) {
            this.destructor = destructor;
            this.thiz = thiz.getVal();
        }

        public LLVMFunctionDescriptor getDestructor() {
            return destructor;
        }

        public LLVMAddress getThiz() {
            return LLVMAddress.fromLong(thiz);
        }
    }

    private static final class LLVMFunctionPointerRegistry {
        private int currentFunctionIndex = 0;
        private final HashMap<LLVMAddress, LLVMFunctionDescriptor> functionDescriptors = new HashMap<>();

        synchronized LLVMFunctionDescriptor getDescriptor(LLVMFunctionHandle handle) {
            return functionDescriptors.get(LLVMAddress.fromLong(handle.getFunctionPointer()));
        }

        synchronized void register(LLVMAddress pointer, LLVMFunctionDescriptor desc) {
            functionDescriptors.put(pointer, desc);
        }

        synchronized LLVMFunctionDescriptor create(FunctionFactory factory) {
            LLVMFunctionDescriptor fn = factory.create(currentFunctionIndex++);
            if (fn.isNullFunction()) {
                assert !functionDescriptors.containsKey(LLVMAddress.nullPointer());
                functionDescriptors.put(LLVMAddress.nullPointer(), fn);
            }
            return fn;
        }

    }

    public LLVMContext(Env env, Map<Class<?>, Object> contextExtension) {
        this.env = env;
        this.contextExtension = contextExtension;

        this.nativeLookup = env.getOptions().get(SulongEngineOption.DISABLE_NFI) ? null : new NativeLookup(env);
        this.nativeCallStatistics = SulongEngineOption.isTrue(env.getOptions().get(SulongEngineOption.NATIVE_CALL_STATS)) ? new HashMap<>() : null;
        this.threadingStack = new LLVMThreadingStack(env.getOptions().get(SulongEngineOption.STACK_SIZE_KB));
        this.sigDfl = LLVMFunctionHandle.createHandle(0);
        this.sigIgn = LLVMFunctionHandle.createHandle(1);
        this.sigErr = LLVMFunctionHandle.createHandle(-1);
        this.toNative = new IdentityHashMap<>();
        this.toManaged = new HashMap<>();
        this.handlesLock = new Object();
        this.functionPointerRegistry = new LLVMFunctionPointerRegistry();
        this.globalScope = LLVMScope.createGlobalScope(this);
        this.sourceContext = new LLVMSourceContext();

        Object mainArgs = env.getConfig().get(LLVMLanguage.MAIN_ARGS_KEY);
        this.mainArguments = mainArgs == null ? env.getApplicationArguments() : (Object[]) mainArgs;

        addLibraryPaths(SulongEngineOption.getPolyglotOptionSearchPaths(env));
        if (nativeLookup != null) {
            List<String> nativeLibraries = SulongEngineOption.getPolyglotOptionNativeLibraries(env);
            nativeLookup.addLibrary(findLibrary("libsulong." + SulongEngineOption.getNativeLibrarySuffix()));
            nativeLookup.addLibraries(nativeLibraries.stream().map(l -> findLibrary(l)).collect(Collectors.toList()));
        }
        addBitcodeLibrary("libsulong.bc");
        List<String> bcLibraries = SulongEngineOption.getPolyglotOptionBCLibraries(env);
        for (String bcl : bcLibraries) {
            addBitcodeLibrary(bcl);
        }
        this.nativeFunctions = new LLVMNativeFunctionsImpl(nativeLookup);
    }

    public <T> T getContextExtension(Class<T> type) {
        return type.cast(contextExtension.get(type));
    }

    public int getByteAlignment(Type type) {
        return type.getAlignment(targetDataLayout);
    }

    public int getByteSize(Type type) {
        return type.getSize(targetDataLayout);
    }

    public int getBytePadding(int offset, Type type) {
        return Type.getPadding(offset, type, targetDataLayout);
    }

    public int getIndexOffset(int index, AggregateType type) {
        return type.getOffsetOf(index, targetDataLayout);
    }

    public DataSpecConverter getDataSpecConverter() {
        return targetDataLayout;
    }

    public void addBitcodeLibrary(String l) {
        CompilerAsserts.neverPartOfCompilation();
        Path p = findLibrary(l);
        if (!bitcodeLibraries.contains(p)) {
            if (!p.toFile().exists()) {
                throw new LinkageError(String.format("Library \"%s\" does not exist.", p.toString()));
            }
            bitcodeLibraries.add(p);
        }
    }

    public List<Path> getBitcodeLibraries() {
        return bitcodeLibraries;
    }

    public void addNativeLibraries(List<String> libraries) {
        if (nativeLookup == null) {
            return;
        }
        nativeLookup.addLibraries(libraries.stream().map(l -> findLibrary(l)).collect(Collectors.toList()));
    }

    public void addLibraryPaths(List<String> paths) {
        for (String p : paths) {
            addLibraryPath(p);
        }
    }

    public void addLibraryPath(String p) {
        Path path = Paths.get(p);
        if (!path.toFile().exists()) {
            System.err.println(String.format("Library Path \"%s\" does not exist.", path.toString()));
        } else {
            if (!libraryPaths.contains(path)) {
                libraryPaths.add(path);
            }
        }
    }

    @TruffleBoundary
    private Path findLibrary(String lib) {
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

        return Paths.get(lib);
    }

    public Env getEnv() {
        return env;
    }

    public LLVMScope getGlobalScope() {
        return globalScope;
    }

    @TruffleBoundary
    public LLVMFunctionDescriptor getFunctionDescriptor(LLVMFunctionHandle handle) {
        return functionPointerRegistry.getDescriptor(handle);
    }

    @TruffleBoundary
    public LLVMFunctionDescriptor createFunctionDescriptor(FunctionFactory factory) {
        return functionPointerRegistry.create(factory);
    }

    @TruffleBoundary
    public TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor) {
        TruffleObject wrapper = null;
        LLVMAddress pointer = null;

        if (nativeLookup != null) {
            try {
                String signature = getNativeSignature(descriptor.getType(), 0);
                TruffleObject createNativeWrapper = nativeLookup.getNativeFunction("@createNativeWrapper", String.format("(env, %s):object", signature));
                try {
                    wrapper = (TruffleObject) ForeignAccess.sendExecute(Message.createExecute(1).createNode(), createNativeWrapper, descriptor);
                    pointer = LLVMAddress.fromLong(ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), wrapper));
                } catch (InteropException ex) {
                    throw new AssertionError(ex);
                }
            } catch (UnsupportedNativeTypeException ex) {
                // ignore, fall back to tagged id
            }
        }

        if (wrapper == null) {
            pointer = LLVMAddress.fromLong(LLVMFunction.tagSulongFunctionPointer(descriptor.getFunctionId()));
            wrapper = new LLVMTruffleAddress(pointer, descriptor.getType(), this);
        }

        functionPointerRegistry.register(pointer, descriptor);
        return wrapper;
    }

    public void setNativeIntrinsicsFactory(NativeIntrinsicProvider nativeIntrinsicsFactory) {
        this.nativeIntrinsicsFactory = nativeIntrinsicsFactory;
    }

    public NativeIntrinsicProvider getNativeIntrinsicsProvider() {
        return nativeIntrinsicsFactory;
    }

    public LLVMFunction getSigDfl() {
        return sigDfl;
    }

    public LLVMFunction getSigIgn() {
        return sigIgn;
    }

    public LLVMFunction getSigErr() {
        return sigErr;
    }

    @TruffleBoundary
    public TruffleObject getManagedObjectForHandle(LLVMAddress address) {
        synchronized (handlesLock) {
            final TruffleObject object = toManaged.get(address);

            if (object == null) {
                throw new UnsupportedOperationException("Cannot resolve native handle: " + address);
            }

            return object;
        }
    }

    @TruffleBoundary
    public void releaseHandle(LLVMAddress address) {
        synchronized (handlesLock) {
            final TruffleObject object = toManaged.get(address);

            if (object == null) {
                throw new UnsupportedOperationException("Cannot resolve native handle: " + address);
            }

            toManaged.remove(address);
            toNative.remove(object);
            LLVMMemory.free(address);
        }
    }

    @TruffleBoundary
    public LLVMAddress getHandleForManagedObject(TruffleObject object) {
        synchronized (handlesLock) {
            return toNative.computeIfAbsent(object, (k) -> {
                LLVMAddress allocatedMemory = LLVMMemory.allocateMemory(Long.BYTES);
                LLVMMemory.putI64(allocatedMemory, 0xdeadbeef);
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

    public void printNativeCallStatistic() {
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

    public LLVMNativeFunctions getNativeFunctions() {
        return nativeFunctions;
    }

    public LinkedList<LLVMAddress> getCaughtExceptionStack() {
        return caughtExceptionStack;
    }

    public LinkedList<DestructorStackElement> getDestructorStack() {
        return destructorStack;
    }

    @TruffleBoundary
    public void addLibraryToNativeLookup(String library) {
        nativeLookup.addLibrary(findLibrary(library));
    }

    public LLVMThreadingStack getThreadingStack() {
        return threadingStack;
    }

    public void setMainArguments(Object[] mainArguments) {
        this.mainArguments = mainArguments;
    }

    public Object[] getMainArguments() {
        return mainArguments;
    }

    public void setMainSourceFile(Source mainSourceFile) {
        this.mainSourceFile = mainSourceFile;
    }

    public Source getMainSourceFile() {
        return mainSourceFile;
    }

    public void registerGlobalVarDealloc(RootCallTarget globalVarDealloc) {
        globalVarDeallocs.add(globalVarDealloc);
    }

    public void registerConstructorFunction(RootCallTarget constructorFunction) {
        constructorFunctions.add(constructorFunction);
    }

    public void registerDestructorFunction(RootCallTarget destructorFunction) {
        destructorFunctions.add(destructorFunction);
    }

    public void registerAtExitFunction(LLVMFunctionDescriptor atExitFunction) {
        atExitFunctions.push(atExitFunction);
    }

    public void registerGlobalVarInit(RootCallTarget globalVarInit) {
        globalVarInits.add(globalVarInit);
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

    public List<RootCallTarget> getGlobalVarDeallocs() {
        return globalVarDeallocs;
    }

    public List<RootCallTarget> getConstructorFunctions() {
        return constructorFunctions;
    }

    public List<RootCallTarget> getDestructorFunctions() {
        return destructorFunctions;
    }

    public Deque<LLVMFunctionDescriptor> getAtExitFunctions() {
        return atExitFunctions;
    }

    public List<RootCallTarget> getGlobalVarInits() {
        return globalVarInits;
    }

    public synchronized List<LLVMThread> getRunningThreads() {
        return Collections.unmodifiableList(runningThreads);
    }

    public boolean bcLibrariesLoaded() {
        return bcLibrariesLoaded;
    }

    public void setBcLibrariesLoaded() {
        bcLibrariesLoaded = true;
    }

    public interface FunctionFactory {
        LLVMFunctionDescriptor create(int index);
    }

    public NativeLookup getNativeLookup() {
        return nativeLookup;
    }

    public static String getNativeSignature(FunctionType type, int skipArguments) throws UnsupportedNativeTypeException {
        return NativeLookup.prepareSignature(type, skipArguments);
    }

    public void setDataLayoutConverter(DataSpecConverterImpl layout) {
        this.targetDataLayout = layout;
    }

    public LLVMSourceContext getSourceContext() {
        return sourceContext;
    }

}
