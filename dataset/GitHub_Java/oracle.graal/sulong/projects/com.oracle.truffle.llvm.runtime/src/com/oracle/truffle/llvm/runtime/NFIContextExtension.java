/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
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

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.runtime.LLVMContext.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeWrapper;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.types.FunctionType;
import com.oracle.truffle.llvm.runtime.types.PointerType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType.PrimitiveKind;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;

public final class NFIContextExtension implements ContextExtension {
    @CompilerDirectives.CompilationFinal private TruffleObject defaultLibraryHandle;
    private final ExternalLibrary defaultLibrary;
    // we use an EconomicMap because iteration order must match the insertion order
    private final EconomicMap<ExternalLibrary, TruffleObject> libraryHandles = EconomicMap.create();
    private final TruffleLanguage.Env env;
    private final LLVMNativeFunctions nativeFunctions;

    public NFIContextExtension(Env env) {
        this.env = env;
        this.defaultLibrary = ExternalLibrary.external("NativeDefault", true);
        this.nativeFunctions = new LLVMNativeFunctions(this);
    }

    @Override
    public void initialize() {
        assert !isInitialized();
        this.defaultLibraryHandle = loadDefaultLibrary();
    }

    public boolean isInitialized() {
        return defaultLibraryHandle != null;
    }

    public static class UnsupportedNativeTypeException extends Exception {

        private static final long serialVersionUID = 1L;

        private final Type type;

        UnsupportedNativeTypeException(Type type) {
            super("unsupported type " + type + " in native interop");
            this.type = type;
        }

        public Type getType() {
            return type;
        }
    }

    public NativePointerIntoLibrary getNativeHandle(LLVMContext context, String name) {
        CompilerAsserts.neverPartOfCompilation();
        try {
            NativeLookupResult result = getNativeDataObjectOrNull(context, name);
            if (result != null) {
                long pointer = ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), result.getObject());
                return new NativePointerIntoLibrary(result.getLibrary(), pointer);
            }
            return null;
        } catch (UnsupportedMessageException e) {
            throw new IllegalStateException(e);
        }
    }

    public LLVMNativeFunctions getNativeSulongFunctions() {
        return nativeFunctions;
    }

    @TruffleBoundary
    public TruffleObject createNativeWrapper(LLVMFunctionDescriptor descriptor) {
        TruffleObject wrapper = null;

        try {
            String signature = getNativeSignature(descriptor.getType(), 0);
            TruffleObject createNativeWrapper = getNativeFunction(descriptor.getContext(), "@createNativeWrapper", String.format("(env, %s):object", signature));
            try {
                wrapper = (TruffleObject) ForeignAccess.sendExecute(Message.EXECUTE.createNode(), createNativeWrapper, new LLVMNativeWrapper(descriptor));
            } catch (InteropException ex) {
                throw new AssertionError(ex);
            }
        } catch (UnsupportedNativeTypeException ex) {
            // ignore, fall back to tagged id
        }
        return wrapper;
    }

    private void addLibraries(LLVMContext context) {
        CompilerAsserts.neverPartOfCompilation();
        context.addInternalLibrary("libsulong." + getNativeLibrarySuffix(), true);
        if (context.getEnv().getOptions().get(SulongEngineOption.LOAD_CXX_LIBRARIES)) {
            // dummy library for C++, see {@link #handleSpecialLibraries}
            context.addInternalLibrary("libsulong++." + getNativeLibrarySuffix(), true);
        }
        List<ExternalLibrary> libraries = context.getExternalLibraries(lib -> lib.isNative());
        for (ExternalLibrary l : libraries) {
            addLibrary(l);
        }
    }

    private void addLibrary(ExternalLibrary lib) throws UnsatisfiedLinkError {
        CompilerAsserts.neverPartOfCompilation();
        if (!libraryHandles.containsKey(lib) && !handleSpecialLibraries(lib)) {
            try {
                libraryHandles.put(lib, loadLibrary(lib));
            } catch (UnsatisfiedLinkError e) {
                System.err.println(lib.toString() + " not found!\n" + e.getMessage());
                throw e;
            }
        }
    }

    public static String getNativeLibrarySuffix() {
        if (System.getProperty("os.name").toLowerCase().contains("mac")) {
            return "dylib";
        } else {
            return "so";
        }
    }

    private boolean handleSpecialLibraries(ExternalLibrary lib) {
        String fileName = lib.getPath().getFileName().toString().trim();
        if (fileName.startsWith("libc.")) {
            // nothing to do, since libsulong.so already links against libc.so
            return true;
        } else if (fileName.startsWith("libsulong++.") || fileName.startsWith("libc++.")) {
            /*
             * Dummy library that doesn't actually exist, but is implicitly replaced by libc++ if
             * available. The libc++ dependency is optional. The bitcode interpreter will still work
             * if it is not found, but C++ programs might not work because of unresolved symbols.
             */
            TruffleObject cxxlib;
            if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                cxxlib = loadLibrary("libc++.dylib", true, null);
            } else {
                cxxlib = loadLibrary("libc++.so.1", true, null);
                if (cxxlib == null) {
                    /*
                     * On Ubuntu, libc++ can not be dynamically loaded because of a missing
                     * dependeny on libgcc_s. Work around this by loading it manually first.
                     */
                    TruffleObject libgcc = loadLibrary("libgcc_s.so.1", true, "RTLD_GLOBAL");
                    if (libgcc != null) {
                        cxxlib = loadLibrary("libc++.so.1", true, null);
                    }
                }
            }
            if (cxxlib != null) {
                libraryHandles.put(lib, cxxlib);
            }
            return true;
        } else {
            return false;
        }
    }

    private TruffleObject loadLibrary(ExternalLibrary lib) {
        CompilerAsserts.neverPartOfCompilation();
        String libName = lib.getPath().toString();
        return loadLibrary(libName, false, null);
    }

    private TruffleObject loadLibrary(String libName, boolean optional, String flags) {
        String loadExpression;
        if (flags == null) {
            loadExpression = String.format("load \"%s\"", libName);
        } else {
            loadExpression = String.format("load(%s) \"%s\"", flags, libName);
        }
        final Source source = Source.newBuilder("nfi", loadExpression, "(load " + libName + ")").build();
        try {
            return (TruffleObject) env.parse(source).call();
        } catch (UnsatisfiedLinkError ex) {
            if (optional) {
                return null;
            } else {
                throw new IllegalArgumentException(loadExpression, ex);
            }
        }
    }

    private TruffleObject loadDefaultLibrary() {
        CompilerAsserts.neverPartOfCompilation();
        final Source source = Source.newBuilder("nfi", "default", "default").build();
        try {
            return (TruffleObject) env.parse(source).call();
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static TruffleObject getNativeFunctionOrNull(TruffleObject library, String name) {
        CompilerAsserts.neverPartOfCompilation();
        String demangledName = name.substring(1);
        if (!KeyInfo.isReadable(ForeignAccess.sendKeyInfo(Message.KEY_INFO.createNode(), library, demangledName))) {
            // try another library
            return null;
        }
        try {
            return (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), library, demangledName);
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String getNativeType(Type type) throws UnsupportedNativeTypeException {
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

    private String[] getNativeTypes(Type[] argTypes, int skipArguments) throws UnsupportedNativeTypeException {
        String[] types = new String[argTypes.length - skipArguments];
        for (int i = skipArguments; i < argTypes.length; i++) {
            types[i - skipArguments] = getNativeType(argTypes[i]);
        }
        return types;
    }

    public NativeLookupResult getNativeFunctionOrNull(LLVMContext context, String name) {
        CompilerAsserts.neverPartOfCompilation();
        addLibraries(context);

        MapCursor<ExternalLibrary, TruffleObject> cursor = libraryHandles.getEntries();
        while (cursor.advance()) {
            TruffleObject symbol = getNativeFunctionOrNull(cursor.getValue(), name);
            if (symbol != null) {
                return new NativeLookupResult(cursor.getKey(), symbol);
            }
        }
        TruffleObject symbol = getNativeFunctionOrNull(defaultLibraryHandle, name);
        if (symbol != null) {
            assert isInitialized();
            return new NativeLookupResult(defaultLibrary, symbol);
        }
        return null;
    }

    private NativeLookupResult getNativeDataObjectOrNull(LLVMContext context, String name) {
        CompilerAsserts.neverPartOfCompilation();
        addLibraries(context);

        String realName = name.substring(1);
        MapCursor<ExternalLibrary, TruffleObject> cursor = libraryHandles.getEntries();
        while (cursor.advance()) {
            TruffleObject symbol = getNativeDataObjectOrNull(cursor.getValue(), realName);
            if (symbol != null) {
                return new NativeLookupResult(cursor.getKey(), symbol);
            }
        }
        TruffleObject symbol = getNativeDataObjectOrNull(defaultLibraryHandle, realName);
        if (symbol != null) {
            assert isInitialized();
            return new NativeLookupResult(defaultLibrary, symbol);
        }
        return null;
    }

    private static TruffleObject getNativeDataObjectOrNull(TruffleObject libraryHandle, String name) {
        try {
            TruffleObject symbol = (TruffleObject) ForeignAccess.sendRead(Message.READ.createNode(), libraryHandle, name);
            if (symbol != null && 0 != ForeignAccess.sendAsPointer(Message.AS_POINTER.createNode(), symbol)) {
                return symbol;
            } else {
                return null;
            }
        } catch (UnknownIdentifierException ex) {
            // try another library
            return null;
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static TruffleObject bindNativeFunction(TruffleObject symbol, String signature) {
        CompilerAsserts.neverPartOfCompilation();
        try {
            return (TruffleObject) ForeignAccess.sendInvoke(Message.INVOKE.createNode(), symbol, "bind", signature);
        } catch (InteropException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public TruffleObject getNativeFunction(LLVMContext context, String name, String signature) {
        CompilerAsserts.neverPartOfCompilation();
        NativeLookupResult result = getNativeFunctionOrNull(context, name);
        if (result != null) {
            return bindNativeFunction(result.getObject(), signature);
        }
        throw new LLVMLinkerException(String.format("External function %s cannot be found.", name));
    }

    public String getNativeSignature(FunctionType type, int skipArguments) throws UnsupportedNativeTypeException {
        CompilerAsserts.neverPartOfCompilation();
        // TODO varargs
        CompilerAsserts.neverPartOfCompilation();
        String nativeRet = getNativeType(type.getReturnType());
        String[] argTypes = getNativeTypes(type.getArgumentTypes(), skipArguments);
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

    public static final class NativeLookupResult {
        private final ExternalLibrary library;
        private final TruffleObject object;

        public NativeLookupResult(ExternalLibrary library, TruffleObject object) {
            this.library = library;
            this.object = object;
        }

        public ExternalLibrary getLibrary() {
            return library;
        }

        public TruffleObject getObject() {
            return object;
        }
    }

    public static final class NativePointerIntoLibrary {
        private final ExternalLibrary library;
        private final long address;

        public NativePointerIntoLibrary(ExternalLibrary library, long address) {
            this.library = library;
            this.address = address;
        }

        public ExternalLibrary getLibrary() {
            return library;
        }

        public long getAddress() {
            return address;
        }
    }
}
