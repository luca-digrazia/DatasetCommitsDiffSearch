/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso._native.nfi;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso._native.Buffer;
import com.oracle.truffle.espresso._native.NativeAccess;
import com.oracle.truffle.espresso._native.NativeAccessProvider;
import com.oracle.truffle.espresso._native.NativeSignature;
import com.oracle.truffle.espresso._native.NativeType;
import com.oracle.truffle.espresso._native.Pointer;
import com.oracle.truffle.espresso._native.RawPointer;
import com.oracle.truffle.espresso._native.TruffleByteBuffer;
import com.oracle.truffle.espresso.jni.NativeLibrary;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.UnsafeAccess;
import com.oracle.truffle.nfi.api.SignatureLibrary;
import com.oracle.truffle.nfi.spi.types.NativeSimpleType;
import com.oracle.truffle.object.DebugCounter;

import sun.misc.Unsafe;

class NFINativeAccess implements NativeAccess {

    private static final Unsafe UNSAFE = UnsafeAccess.get();

    private static final boolean CACHE_SIGNATURES = "true".equals(System.getProperty("espresso.nfi.cache_signatures", "true"));

    private final DebugCounter nfiSignaturesCreated = DebugCounter.create("NFI signatures created");

    private final Map<NativeSignature, Object> signatureCache;

    protected final InteropLibrary uncachedInterop = InteropLibrary.getUncached();
    protected final SignatureLibrary uncachedSignature = SignatureLibrary.getUncached();

    private final EspressoContext context;

    protected EspressoContext getContext() {
        return context;
    }

    protected static NativeSimpleType nfiType(NativeType nativeType) {
        // @formatter:off
        switch (nativeType) {
            case VOID:    return NativeSimpleType.VOID;
            case BOOLEAN: // fall-through
            case BYTE:    return NativeSimpleType.SINT8;
            case CHAR:    // fall-through
            case SHORT:   return NativeSimpleType.SINT16;
            case INT:     return NativeSimpleType.SINT32;
            case LONG:    return NativeSimpleType.SINT64;
            case FLOAT:   return NativeSimpleType.FLOAT;
            case DOUBLE:  return NativeSimpleType.DOUBLE;
            case OBJECT:  return NativeSimpleType.SINT64; // word-sized handle
            case POINTER: return NativeSimpleType.POINTER;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("Unexpected: " + nativeType);
        }
        // @formatter:on
    }

    protected static String nfiStringSignature(NativeSignature nativeSignature) {
        StringBuilder sb = new StringBuilder(64);
        sb.append('(');
        boolean isFirst = true;
        for (int i = 0; i < nativeSignature.getParameterCount(); ++i) {
            if (!isFirst) {
                sb.append(',');
            }
            sb.append(nfiType(nativeSignature.parameterTypeAt(i)));
            isFirst = false;
        }
        sb.append(')');
        sb.append(':');
        sb.append(nfiType(nativeSignature.getReturnType()));
        return sb.toString();
    }

    @FunctionalInterface
    private interface SignatureProvider extends Function<NativeSignature, Object> {
    }

    final SignatureProvider signatureProvider = new SignatureProvider() {
        @Override
        public Object apply(NativeSignature nativeSignature) {
            nfiSignaturesCreated.inc();
            Source source = Source.newBuilder("nfi",
                            nfiStringSignature(nativeSignature), "signature").build();
            CallTarget target = getContext().getEnv().parseInternal(source);
            return target.call();
        }
    };

    protected Object createNFISignature(NativeSignature nativeSignature) {
        return CACHE_SIGNATURES
                        ? signatureCache.computeIfAbsent(nativeSignature, signatureProvider)
                        : signatureProvider.apply(nativeSignature);
    }

    public NFINativeAccess(EspressoContext context) {
        this.context = context;
        signatureCache = CACHE_SIGNATURES
                        ? new ConcurrentHashMap<>()
                        : null;
    }

    @Override
    public @Pointer TruffleObject loadLibrary(Path libraryPath) {
        CompilerAsserts.neverPartOfCompilation();
        String nfiSource = String.format("load(RTLD_LAZY) '%s'", libraryPath);
        return loadLibraryHelper(nfiSource);
    }

    protected @Pointer TruffleObject loadLibraryHelper(String nfiSource) {
        Source source = Source.newBuilder("nfi", nfiSource, "loadLibrary").build();
        CallTarget target = getContext().getEnv().parseInternal(source);
        try {
            return (TruffleObject) target.call();
        } catch (IllegalArgumentException e) {
            getContext().getLogger().log(Level.SEVERE, "TruffleNFI native library isolation is not supported.", e);
            throw EspressoError.shouldNotReachHere(e);
        } catch (AbstractTruffleException e) {
            // TODO(peterssen): Remove assert once GR-27045 reaches a definitive consensus.
            assert "com.oracle.truffle.nfi.impl.NFIUnsatisfiedLinkError".equals(e.getClass().getName());
            // We treat AbstractTruffleException as if it were an UnsatisfiedLinkError.
            TruffleLogger.getLogger(EspressoLanguage.ID, NFIIsolatedNativeAccess.class).fine(e.getMessage());
            return null;
        }
    }

    @Override
    public void unloadLibrary(@Pointer TruffleObject library) {
        // nop
    }

    @Override
    public @Pointer TruffleObject lookupSymbol(@Pointer TruffleObject library, String symbolName) {
        try {
            return (TruffleObject) uncachedInterop.readMember(library, symbolName);
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        } catch (UnknownIdentifierException e) {
            return null;
        }
    }

    @ExportLibrary(InteropLibrary.class)
    static final class NativeToJavaWrapper implements TruffleObject {

        final TruffleObject delegate;
        final NativeSignature nativeSignature;

        NativeToJavaWrapper(TruffleObject executable, NativeSignature nativeSignature) {
            this.delegate = executable;
            this.nativeSignature = nativeSignature;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExplodeLoop
        @ExportMessage
        Object execute(Object[] arguments, @CachedLibrary("this.delegate") InteropLibrary interop) throws ArityException {
            final int paramCount = nativeSignature.getParameterCount();
            if (arguments.length != paramCount) {
                CompilerDirectives.transferToInterpreter();
                throw ArityException.create(paramCount, arguments.length);
            }
            try {
                Object[] convertedArgs = new Object[arguments.length];
                for (int i = 0; i < paramCount; i++) {
                    NativeType param = nativeSignature.parameterTypeAt(i);
                    switch (param) {
                        case BOOLEAN:
                            convertedArgs[i] = ((boolean) arguments[i]) ? (byte) 1 : (byte) 0;
                            break;
                        case CHAR:
                            convertedArgs[i] = (short) (char) arguments[i];
                            break;
                        default:
                            convertedArgs[i] = arguments[i];
                    }
                }
                Object ret = interop.execute(delegate, convertedArgs);
                switch (nativeSignature.getReturnType()) {
                    case BOOLEAN:
                        ret = (byte) ret != 0;
                        break;
                    case CHAR:
                        ret = (char) (short) ret;
                        break;
                    case VOID:
                        ret = StaticObject.NULL;
                        break;
                }
                return ret;
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }

    @Override
    public @Pointer TruffleObject bindSymbol(@Pointer TruffleObject symbol, NativeSignature nativeSignature) {
        if (uncachedInterop.isNull(symbol)) {
            return null; // LD_DEBUG=unused makes non-existing symbols to be NULL.
        }
        TruffleObject executable = (TruffleObject) uncachedSignature.bind(createNFISignature(nativeSignature), symbol);
        assert uncachedInterop.isExecutable(executable);
        return new NativeToJavaWrapper(executable, nativeSignature);
    }

    @Override
    public @Pointer TruffleObject createNativeClosure(TruffleObject executable, NativeSignature nativeSignature) {
        assert uncachedInterop.isExecutable(executable);
        TruffleObject wrappedExecutable = new JavaToNativeWrapper(executable, nativeSignature);
        TruffleObject nativeFn = (TruffleObject) uncachedSignature.createClosure(createNFISignature(nativeSignature), wrappedExecutable);
        assert uncachedInterop.isPointer(nativeFn);
        return nativeFn;
    }

    @ExportLibrary(InteropLibrary.class)
    static final class JavaToNativeWrapper implements TruffleObject {

        final TruffleObject delegate;
        final NativeSignature nativeSignature;

        JavaToNativeWrapper(TruffleObject executable, NativeSignature nativeSignature) {
            this.delegate = executable;
            this.nativeSignature = nativeSignature;
        }

        @SuppressWarnings("static-method")
        @ExportMessage
        boolean isExecutable() {
            return true;
        }

        @ExplodeLoop
        @ExportMessage
        Object execute(Object[] arguments, @CachedLibrary("this.delegate") InteropLibrary interop) throws ArityException {
            final int paramCount = nativeSignature.getParameterCount();
            if (arguments.length != paramCount) {
                CompilerDirectives.transferToInterpreter();
                throw ArityException.create(paramCount, arguments.length);
            }
            try {
                Object[] convertedArgs = new Object[arguments.length];
                for (int i = 0; i < paramCount; i++) {
                    NativeType param = nativeSignature.parameterTypeAt(i);
                    switch (param) {
                        case BOOLEAN:
                            convertedArgs[i] = (byte) arguments[i] != 0;
                            break;
                        case CHAR:
                            convertedArgs[i] = (char) (short) arguments[i];
                            break;
                        default:
                            convertedArgs[i] = arguments[i];
                    }
                }
                Object ret = interop.execute(delegate, convertedArgs);
                switch (nativeSignature.getReturnType()) {
                    case BOOLEAN:
                        ret = ((boolean) ret) ? (byte) 1 : (byte) 0;
                        break;
                    case CHAR:
                        ret = (short) (char) ret;
                        break;
                    case VOID:
                        ret = StaticObject.NULL;
                        break;
                }
                return ret;
            } catch (UnsupportedTypeException | UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }

    @Override
    public @Buffer TruffleObject allocateMemory(long size) {
        long address = 0L;
        try {
            address = UNSAFE.allocateMemory(size);
        } catch (OutOfMemoryError e) {
            return null;
        }
        return TruffleByteBuffer.wrap(RawPointer.create(address), Math.toIntExact(size));
    }

    @Override
    public @Buffer TruffleObject reallocateMemory(@Pointer TruffleObject buffer, long newSize) {
        assert InteropLibrary.getUncached().isPointer(buffer);
        long oldAddress = 0L;
        try {
            oldAddress = InteropLibrary.getUncached().asPointer(buffer);
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        long newAddress = 0L;
        try {
            newAddress = UNSAFE.reallocateMemory(oldAddress, newSize);
        } catch (OutOfMemoryError e) {
            return null;
        }
        return TruffleByteBuffer.wrap(RawPointer.create(newAddress), Math.toIntExact(newSize));
    }

    @Override
    public void freeMemory(@Pointer TruffleObject buffer) {
        assert InteropLibrary.getUncached().isPointer(buffer);
        long address = 0L;
        try {
            address = InteropLibrary.getUncached().asPointer(buffer);
        } catch (UnsupportedMessageException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
        UNSAFE.freeMemory(address);
    }

    public final class Provider implements NativeAccessProvider {
        @Override
        public String id() {
            return "nfi-native";
        }

        @Override
        public NativeAccess create(EspressoContext context) {
            return new NFINativeAccess(context);
        }
    }

}
