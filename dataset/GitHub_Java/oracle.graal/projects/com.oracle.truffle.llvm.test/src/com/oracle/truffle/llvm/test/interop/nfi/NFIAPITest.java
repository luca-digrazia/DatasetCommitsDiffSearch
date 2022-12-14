/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.test.interop.nfi;

import java.io.File;
import java.nio.file.Path;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.test.options.TestOptions;
import com.oracle.truffle.tck.TruffleRunner;

public class NFIAPITest {

    @ClassRule public static TruffleRunner.RunWithPolyglotRule runWithPolyglot = new TruffleRunner.RunWithPolyglotRule();

    private static final Path TEST_DIR = new File(TestOptions.TEST_SUITE_PATH, "nfi").toPath();
    private static final String SULONG_FILENAME = "O0_MEM2REG.bc";

    public static TruffleObject sulongObject;
    public static CallTarget lookupAndBind;

    @BeforeClass
    public static void initialize() {
        sulongObject = loadLibrary("basicTest", SULONG_FILENAME, "application/x-sulong");
        lookupAndBind = lookupAndBind();
    }

    private static CallTarget lookupAndBind() {
        return Truffle.getRuntime().createCallTarget(new LookupAndBindNode());
    }

    private static TruffleObject loadLibrary(String lib, String filename, String mimetype) {
        File file = new File(TEST_DIR.toFile(), lib + "/" + filename);
        String loadLib = "load '" + file.getAbsolutePath() + "'";
        Source source = Source.newBuilder("llvm", loadLib, "loabLibrary").mimeType(mimetype).build();
        CallTarget target = runWithPolyglot.getTruffleTestEnv().parse(source);
        return (TruffleObject) target.call();
    }

    private static final class LookupAndBindNode extends RootNode {

        @Child private Node lookupSymbol = Message.READ.createNode();
        @Child private Node bind = Message.INVOKE.createNode();

        private LookupAndBindNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            TruffleObject library = (TruffleObject) frame.getArguments()[0];
            String symbolName = (String) frame.getArguments()[1];
            String signature = (String) frame.getArguments()[2];

            try {
                TruffleObject symbol = (TruffleObject) ForeignAccess.sendRead(lookupSymbol, library, symbolName);
                return ForeignAccess.sendInvoke(bind, symbol, "bind", signature);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }
    }

    protected abstract static class TestRootNode extends RootNode {

        protected TestRootNode() {
            super(null);
        }

        @TruffleBoundary
        protected static void assertEquals(Object expected, Object actual) {
            Assert.assertEquals(expected, actual);
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            try {
                return executeTest(frame);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }

        public abstract Object executeTest(VirtualFrame frame) throws InteropException;
    }

    protected static class SendExecuteNode extends TestRootNode {

        private final TruffleObject receiver;

        @Child private Node execute;

        protected SendExecuteNode(TruffleObject library, String symbol, String signature) {
            this(lookupAndBind(library, symbol, signature));
        }

        protected SendExecuteNode(TruffleObject receiver) {
            this.receiver = receiver;
            execute = Message.EXECUTE.createNode();
        }

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            return ForeignAccess.sendExecute(execute, receiver, frame.getArguments());
        }
    }

    protected static TruffleObject lookupAndBind(TruffleObject lib, String name, String signature) {
        return (TruffleObject) lookupAndBind.call(lib, name, signature);
    }

    protected static boolean isBoxed(TruffleObject obj) {
        return ForeignAccess.sendIsBoxed(Message.IS_BOXED.createNode(), obj);
    }

    protected static Object unbox(TruffleObject obj) {
        try {
            return ForeignAccess.sendUnbox(Message.UNBOX.createNode(), obj);
        } catch (UnsupportedMessageException e) {
            throw new AssertionError(e);
        }
    }

    protected static boolean isNull(TruffleObject foreignObject) {
        return ForeignAccess.sendIsNull(Message.IS_NULL.createNode(), foreignObject);
    }
}
