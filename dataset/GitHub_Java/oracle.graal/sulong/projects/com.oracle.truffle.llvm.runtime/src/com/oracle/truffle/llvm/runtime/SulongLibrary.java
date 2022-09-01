/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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


import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;
import com.oracle.truffle.llvm.runtime.interop.LLVMForeignCallNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMNode;

/**
 * Object that is returned when a bitcode library is parsed.
 */
@ExportLibrary(InteropLibrary.class)
public final class SulongLibrary implements TruffleObject {

    private final String name;
    private final LLVMScope scope;
    private final CallTarget main;

    public SulongLibrary(String name, LLVMScope scope, CallTarget main) {
        this.name = name;
        this.scope = scope;
        this.main = main;
    }

    private LLVMFunctionDescriptor lookupFunctionDescriptor(String symbolName) {
        LLVMSymbol symbol = scope.get(symbolName);
        if (symbol != null && symbol.isFunction()) {
            return symbol.asFunction();
        }
        return null;
    }

    public String getName() {
        return name;
    }

    @GenerateUncached
    abstract static class LookupNode extends LLVMNode {

        abstract LLVMFunctionDescriptor execute(SulongLibrary library, String name);

        @Specialization(guards = {"library == cachedLibrary", "name.equals(cachedName)"})
        @SuppressWarnings("unused")
        LLVMFunctionDescriptor doCached(SulongLibrary library, String name,
                        @Cached("library") SulongLibrary cachedLibrary,
                        @Cached("name") String cachedName,
                        @Cached("lookupFunctionDescriptor(cachedLibrary, cachedName)") LLVMFunctionDescriptor cachedDescriptor) {
            return cachedDescriptor;
        }

        @Specialization(replaces = "doCached")
        @TruffleBoundary
        static LLVMFunctionDescriptor doGeneric(SulongLibrary library, String name) {
            return lookupFunctionDescriptor(library, name);
        }

        protected static LLVMFunctionDescriptor lookupFunctionDescriptor(SulongLibrary library, String name) {
            if (name.startsWith("@")) {
                // safeguard: external users are never supposed to see the "@"
                // TODO remove after getting rid of the @
                return null;
            }

            String atname = "@" + name;
            LLVMFunctionDescriptor d = library.lookupFunctionDescriptor(atname);
            if (d != null) {
                return d;
            }
            return library.lookupFunctionDescriptor(name);
        }
    }

    @ExportMessage
    Object readMember(String name,
                    @Shared("lookup") @Cached LookupNode lookup) throws UnknownIdentifierException {
        Object ret = lookup.execute(this, name);
        if (ret == null) {
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.create(name);
        }
        return ret;
    }

    @ExportMessage
    Object invokeMember(String name, Object[] arguments,
                    @Shared("lookup") @Cached LookupNode lookup,
                    @Cached LLVMForeignCallNode call) throws ArityException, UnknownIdentifierException, UnsupportedTypeException {
        LLVMFunctionDescriptor fn = lookup.execute(this, name);
        if (fn == null) {
            CompilerDirectives.transferToInterpreter();
            throw UnknownIdentifierException.create(name);
        }

        return call.executeCall(fn, arguments);
    }

    @ExportMessage
    boolean hasMembers() {
        return true;
    }

    @ExportMessage
    Object getMembers(boolean includeInternal) {
        return scope.getKeys();
    }

    @ExportMessage(name = "isMemberReadable")
    @ExportMessage(name = "isMemberInvocable")
    boolean memberExists(String name,
                    @Shared("lookup") @Cached LookupNode lookup) {
        return lookup.execute(this, name) != null;
    }

    @ExportMessage
    boolean isExecutable() {
        return main != null;
    }

    @ExportMessage
    abstract static class Execute {

        @Specialization(guards = "library == cachedLibrary")
        @SuppressWarnings("unused")
        static Object doCached(SulongLibrary library, Object[] args,
                        @Cached("library") SulongLibrary cachedLibrary,
                        @Cached("createMainCall(cachedLibrary)") DirectCallNode call) {
            return call.call(args);
        }

        static DirectCallNode createMainCall(SulongLibrary library) {
            return DirectCallNode.create(library.main);
        }

        @Specialization(replaces = "doCached")
        static Object doGeneric(SulongLibrary library, Object[] args,
                        @Cached("create()") IndirectCallNode call) {
            return call.call(library.main, args);
        }
    }
}
