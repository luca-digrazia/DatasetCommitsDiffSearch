/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nativemode.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunction;
import com.oracle.truffle.llvm.runtime.LLVMFunctionCode;
import com.oracle.truffle.llvm.runtime.LibraryLocator;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeWrapper;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.nfi.api.SignatureLibrary;
import org.graalvm.collections.Equivalence;

public final class NFIContextExtension extends NativeContextExtension {

    private static final class SignatureSourceCache {

        private final ArrayList<WeakHashMap<FunctionType, Source>> cache;

        SignatureSourceCache() {
            // skipArgs is always either 0 or 1
            cache = new ArrayList<>(2);
            cache.add(new WeakHashMap<>());
            cache.add(new WeakHashMap<>());
        }

        synchronized Source get(FunctionType type, int skipArgs) throws UnsupportedNativeTypeException {
            WeakHashMap<FunctionType, Source> map = cache.get(skipArgs);
            Source ret = map.get(type);
            if (ret == null) {
                String sig = getNativeSignature(type, skipArgs);
                ret = Source.newBuilder("nfi", sig, "llvm-nfi-signature").build();
                map.put(type, ret);
            }
            return ret;
        }
    }

    public static final class Factory implements ContextExtension.Factory<NativeContextExtension> {

        // share the SignatureSourceCache between contexts
        private final SignatureSourceCache signatureSourceCache = new SignatureSourceCache();

        @Override
        public NativeContextExtension create(Env env) {
            return new NFIContextExtension(env, signatureSourceCache);
        }
    }

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    @CompilerDirectives.CompilationFinal private Object defaultLibraryHandle;
    private boolean internalLibrariesAdded = false;
    private final List<Object> libraryHandles = new ArrayList<>();
    private final EconomicMap<TruffleFile, CallTarget> visited = EconomicMap.create();
    private final Env env;

    private final SignatureSourceCache signatureSourceCache;
    private final EconomicMap<Source, Object> signatureCache = EconomicMap.create(Equivalence.IDENTITY);

    private NFIContextExtension(Env env, SignatureSourceCache signatureSourceCache) {
        assert env.getOptions().get(SulongEngineOption.ENABLE_NFI);
        this.env = env;
        this.signatureSourceCache = signatureSourceCache;
    }

    @Override
    public void initialize(LLVMContext context) {
        assert !isInitialized();
        if (!internalLibrariesAdded) {
            TruffleFile file = locateInternalLibrary(context, "libsulong-native." + getNativeLibrarySuffix(), "<default nfi library>");
            Object lib = loadLibrary(file, context);
            if (lib instanceof CallTarget) {
                libraryHandles.add(((CallTarget) lib).call());
            }

            Object defaultLib = loadDefaultLibrary();
            if (defaultLib instanceof CallTarget) {
                this.defaultLibraryHandle = ((CallTarget) defaultLib).call();
            }
            internalLibrariesAdded = true;
        }
    }

    public boolean isInitialized() {
        return defaultLibraryHandle != null;
    }

    @Override
    public NativePointerIntoLibrary getNativeHandle(String name) {
        CompilerAsserts.neverPartOfCompilation();
        try {
            NativeLookupResult result = getNativeDataObjectOrNull(name);
            if (result != null) {
                long pointer = INTEROP.asPointer(result.getObject());
                return new NativePointerIntoLibrary(pointer);
            }
            return null;
        } catch (UnsupportedMessageException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    @TruffleBoundary
    public Object createNativeWrapper(LLVMFunction function, LLVMFunctionCode code) {
        Object wrapper = null;

        try {
            Source signatureSource = getNativeSignatureSource(function.getType(), 0);
            Object signature = getCachedSignature(signatureSource);
            wrapper = SignatureLibrary.getUncached().createClosure(signature, new LLVMNativeWrapper(function, code));
        } catch (UnsupportedNativeTypeException ex) {
            // ignore, fall back to tagged id
        }
        return wrapper;
    }

    @Override
    public synchronized void addLibraryHandles(Object library) {
        CompilerAsserts.neverPartOfCompilation();
        if (!libraryHandles.contains(library)) {
            libraryHandles.add(library);
        }
    }

    @Override
    public synchronized CallTarget parseNativeLibrary(TruffleFile file, LLVMContext context) throws UnsatisfiedLinkError {
        CompilerAsserts.neverPartOfCompilation();
        try {
            if (!visited.containsKey(file)) {
                Object callTarget = loadLibrary(file, context);
                if (callTarget != null) {
                    visited.put(file, (CallTarget) callTarget);
                    return (CallTarget) callTarget;
                } else {
                    throw new IllegalStateException("Native library call target is null.");
                }
            } else {
                return visited.get(file);
            }
        } catch (UnsatisfiedLinkError e) {
            System.err.println(file.toString() + " not found!\n" + e.getMessage());
            throw e;
        }
    }

    public static String getNativeLibrarySuffix() {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            return "dylib";
        } else {
            return "so";
        }
    }

    public static String getNativeLibrarySuffixVersioned(int version) {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            return version + ".dylib";
        } else {
            return "so." + version;
        }
    }

    private Object loadLibrary(TruffleFile file, LLVMContext context) {
        CompilerAsserts.neverPartOfCompilation();
        String libName = file.getPath();
        return loadLibrary(libName, false, null, context, file);
    }

    private Object loadLibrary(String libName, boolean optional, String flags, LLVMContext context, Object file) {
        LibraryLocator.traceLoadNative(context, file);
        String loadExpression;
        if (flags == null) {
            loadExpression = String.format("load \"%s\"", libName);
        } else {
            loadExpression = String.format("load(%s) \"%s\"", flags, libName);
        }
        final Source source = Source.newBuilder("nfi", loadExpression, "(load " + libName + ")").internal(true).build();
        try {
            // remove the call to the calltarget
            return env.parseInternal(source);
        } catch (UnsatisfiedLinkError ex) {
            if (optional) {
                return null;
            } else {
                throw ex;
            }
        }
    }

    private Object loadDefaultLibrary() {
        CompilerAsserts.neverPartOfCompilation();
        final Source source = Source.newBuilder("nfi", "default", "default").internal(true).build();
        try {
            // remove the call to the calltarget
            return env.parseInternal(source);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static Object getNativeFunctionOrNull(Object library, String name) {
        CompilerAsserts.neverPartOfCompilation();
        if (!INTEROP.isMemberReadable(library, name)) {
            // try another library
            return null;
        }
        try {
            return INTEROP.readMember(library, name);
        } catch (UnknownIdentifierException ex) {
            return null;
        } catch (InteropException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String getNativeType(Type type) throws UnsupportedNativeTypeException {
        if (type instanceof FunctionType) {
            return getNativeSignature((FunctionType) type, 0);
        } else if (type instanceof PointerType && ((PointerType) type).getPointeeType() instanceof FunctionType) {
            FunctionType functionType = (FunctionType) ((PointerType) type).getPointeeType();
            return getNativeSignature(functionType, 0);
        } else if (type instanceof PointerType) {
            return "POINTER";
        } else if (type instanceof PrimitiveType) {
            PrimitiveType primitiveType = (PrimitiveType) type;
            PrimitiveKind kind = primitiveType.getPrimitiveKind();
            switch (kind) {
                case I1:
                case I8:
                    return "SINT8";
                case I16:
                    return "SINT16";
                case I32:
                    return "SINT32";
                case I64:
                    return "SINT64";
                case FLOAT:
                    return "FLOAT";
                case DOUBLE:
                    return "DOUBLE";
                default:
                    throw new UnsupportedNativeTypeException(primitiveType);

            }
        } else if (type instanceof VoidType) {
            return "VOID";
        }
        throw new UnsupportedNativeTypeException(type);
    }

    private static String[] getNativeArgumentTypes(FunctionType functionType, int skipArguments) throws UnsupportedNativeTypeException {
        String[] types = new String[functionType.getNumberOfArguments() - skipArguments];
        for (int i = skipArguments; i < functionType.getNumberOfArguments(); i++) {
            types[i - skipArguments] = getNativeType(functionType.getArgumentType(i));
        }
        return types;
    }

    @Override
    public synchronized NativeLookupResult getNativeFunctionOrNull(String name) {
        CompilerAsserts.neverPartOfCompilation();
        Object[] cursor = libraryHandles.toArray();
        for (int i = 0; i < cursor.length; i++) {
            Object symbol = getNativeFunctionOrNull(cursor[i], name);
            if (symbol != null) {
                return new NativeLookupResult(symbol);
            }
        }
        Object symbol = getNativeFunctionOrNull(defaultLibraryHandle, name);
        if (symbol != null) {
            assert isInitialized();
            return new NativeLookupResult(symbol);
        }
        return null;
    }

    private synchronized NativeLookupResult getNativeDataObjectOrNull(String name) {
        CompilerAsserts.neverPartOfCompilation();
        Object[] cursor = libraryHandles.toArray();
        for (int i = 0; i < cursor.length; i++) {
            Object symbol = getNativeFunctionOrNull(cursor[i], name);
            if (symbol != null) {
                return new NativeLookupResult(symbol);
            }
        }
        Object symbol = getNativeDataObjectOrNull(defaultLibraryHandle, name);
        if (symbol != null) {
            assert isInitialized();
            return new NativeLookupResult(symbol);
        }
        return null;
    }

    private static Object getNativeDataObjectOrNull(Object libraryHandle, String name) {
        try {
            Object symbol = INTEROP.readMember(libraryHandle, name);
            if (symbol != null && 0 != INTEROP.asPointer(symbol)) {
                return symbol;
            } else {
                return null;
            }
        } catch (UnknownIdentifierException ex) {
            // try another library
            return null;
        } catch (InteropException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Object bindNativeFunction(Object symbol, String signature) {
        CompilerAsserts.neverPartOfCompilation();
        try {
            return INTEROP.invokeMember(symbol, "bind", signature);
        } catch (InteropException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Override
    public Object getNativeFunction(String name, String signature) {
        CompilerAsserts.neverPartOfCompilation();
        NativeLookupResult result = getNativeFunctionOrNull(name);
        if (result != null) {
            return bindNativeFunction(result.getObject(), signature);
        }
        throw new LLVMLinkerException(String.format("External function %s cannot be found.", name));
    }

    @Override
    public Source getNativeSignatureSource(FunctionType type, int skipArguments) throws UnsupportedNativeTypeException {
        CompilerAsserts.neverPartOfCompilation();
        return signatureSourceCache.get(type, skipArguments);
    }

    @TruffleBoundary
    private Object createSignature(Source signatureSource) {
        synchronized (signatureCache) {
            Object ret = signatureCache.get(signatureSource);
            if (ret == null) {
                CallTarget createSignature = env.parseInternal(signatureSource);
                ret = createSignature.call();
                signatureCache.put(signatureSource, ret);
            }
            return ret;
        }
    }

    private Object getCachedSignature(Source signatureSource) {
        Object ret = signatureCache.get(signatureSource);
        if (ret == null) {
            ret = createSignature(signatureSource);
        }
        return ret;
    }

    @Override
    public Object bindSignature(LLVMFunctionCode function, Source signatureSource) {
        // TODO(rs): make a fast-path version of this code
        CompilerAsserts.neverPartOfCompilation();
        Object nativeFunction = function.getNativeFunctionSlowPath();
        Object signature = getCachedSignature(signatureSource);
        return SignatureLibrary.getUncached().bind(signature, nativeFunction);
    }

    @Override
    public Object bindSignature(long fnPtr, Source signatureSource) {
        // TODO(rs): make a fast-path version of this code
        CompilerAsserts.neverPartOfCompilation();
        Object signature = getCachedSignature(signatureSource);
        return SignatureLibrary.getUncached().bind(signature, LLVMNativePointer.create(fnPtr));
    }

    private static String getNativeSignature(FunctionType type, int skipArguments) throws UnsupportedNativeTypeException {
        // TODO varargs
        CompilerAsserts.neverPartOfCompilation();
        String nativeRet = getNativeType(type.getReturnType());
        String[] argTypes = getNativeArgumentTypes(type, skipArguments);
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        for (String a : argTypes) {
            sb.append(a);
            sb.append(",");
        }
        if (argTypes.length > 0) {
            sb.setCharAt(sb.length() - 1, ')');
        } else {
            sb.append(')');
        }
        sb.append(":");
        sb.append(nativeRet);
        return sb.toString();
    }

}
