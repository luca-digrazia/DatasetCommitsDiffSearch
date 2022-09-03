/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.instruments.trace;

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Registration;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;

@Registration(id = LLVMTracerInstrument.ID, name = LLVMTracerInstrument.NAME, services = LLVMTracerInstrument.class)
public final class LLVMTracerInstrument extends TruffleInstrument {

    static final String ID = "TraceLLVM";
    static final String NAME = "LLVMTracerInstrument";

    @Option(name = "", category = OptionCategory.DEBUG, help = "Enable tracing of executed instructions (defaults to \'stdout\', can be set to \'stderr\').") //
    static final OptionKey<String> TRACELLVM = new OptionKey<>(String.valueOf("stdout"));

    private PrintStream traceTarget;

    public LLVMTracerInstrument() {
        traceTarget = null;
    }

    @Override
    protected void onCreate(Env env) {
        env.registerService(this);

        final SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder();
        builder.mimeTypeIs("text/x-llvmir");
        builder.tagIs(StandardTags.StatementTag.class, StandardTags.RootTag.class);
        final SourceSectionFilter filter = builder.build();

        final Instrumenter instrumenter = env.getInstrumenter();
        traceTarget = createTargetStream(env, env.getOptions().get(LLVMTracerInstrument.TRACELLVM));
        instrumenter.attachExecutionEventFactory(filter, new LLVMTraceNodeFactory(traceTarget));
    }

    @Override
    @TruffleBoundary
    protected void onDispose(Env env) {
        traceTarget.flush();

        final String target = env.getOptions().get(LLVMTracerInstrument.TRACELLVM);
        assert target != null : "Invalid modification of tracing target!";

        switch (target) {
            case "out":
            case "stdout":
            case "err":
            case "stderr":
                break;
            default:
                traceTarget.close();
                break;
        }
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return new LLVMTracerInstrumentOptionDescriptors();
    }

    private static final String FILE_TARGET_PREFIX = "file://";

    @TruffleBoundary
    private static PrintStream createTargetStream(TruffleInstrument.Env env, String target) {
        if (target == null) {
            throw new IllegalArgumentException("Target for trace unspecified!");
        }

        final OutputStream targetStream;
        switch (target) {
            case "out":
            case "stdout":
                targetStream = env.out();
                break;

            case "err":
            case "stderr":
                targetStream = env.err();
                break;

            default:
                if (target.startsWith(FILE_TARGET_PREFIX)) {
                    final String fileName = target.substring(FILE_TARGET_PREFIX.length());
                    try {
                        targetStream = new BufferedOutputStream(new FileOutputStream(fileName, true));
                    } catch (FileNotFoundException e) {
                        throw new IllegalArgumentException("Invalid file: " + fileName, e);
                    }
                } else {
                    throw new IllegalArgumentException("Invalid target for tracing: " + target);
                }
        }

        return new PrintStream(targetStream);
    }
}
