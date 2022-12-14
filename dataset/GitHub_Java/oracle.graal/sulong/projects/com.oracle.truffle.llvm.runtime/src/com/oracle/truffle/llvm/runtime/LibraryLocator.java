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

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * Encapsulates logic for locating libraries.
 */
public abstract class LibraryLocator {

    @CompilerDirectives.TruffleBoundary
    public Path locate(LLVMContext context, String lib, Object reason) {
        if (context.ldDebugEnabled()) {
            LibraryLocator.traceLoader(context, "\n");
        }
        traceFind(context, lib, reason);
        return locateLibrary(context, lib, reason);
    }

    protected abstract Path locateLibrary(LLVMContext context, String lib, Object reason);

    public static void traceFind(LLVMContext context, Object lib, Object reason) {
        if (context.ldDebugEnabled()) {
            traceLoader(context, "find external library=%s; needed by %s\n", lib, reason);
        }
    }

    public static void traceTry(LLVMContext context, Object file) {
        if (context.ldDebugEnabled()) {
            traceLoader(context, "  trying file=%s\n", file);
        }
    }

    public static void traceSearchPath(LLVMContext context, List<?> paths) {
        if (context.ldDebugEnabled()) {
            traceLoader(context, " search path=%s\n", paths);
        }
    }

    public static void traceSearchPath(LLVMContext context, List<?> paths, Object reason) {
        if (context.ldDebugEnabled()) {
            traceLoader(context, " search path=%s (local path from %s)\n", paths, reason);
        }
    }

    public static void traceParseBitcode(LLVMContext context, Object path) {
        if (context.ldDebugEnabled()) {
            traceLoader(context, "parse bitcode=%s\n", path);
        }
    }

    public static void traceAlreadyLoaded(LLVMContext context, Object path) {
        if (context.ldDebugEnabled()) {
            traceLoader(context, "library already located: %s\n", path);
        }
    }

    @CompilerDirectives.TruffleBoundary
    private static void traceLoader(LLVMContext context, String str) {
        PrintStream stream = context.ldDebugStream();
        printPrefix(stream, context);
        stream.print(str);
    }

    @CompilerDirectives.TruffleBoundary
    private static void traceLoader(LLVMContext context, String format, Object arg0) {
        PrintStream stream = context.ldDebugStream();
        printPrefix(stream, context);
        stream.printf(format, arg0);
    }

    @CompilerDirectives.TruffleBoundary
    private static void traceLoader(LLVMContext context, String format, Object arg0, Object arg1) {
        PrintStream stream = context.ldDebugStream();
        printPrefix(stream, context);
        stream.printf(format, arg0, arg1);
    }

    private static void printPrefix(PrintStream stream, LLVMContext context) {
        stream.printf("lli(%x): ", System.identityHashCode(context));
    }
}
