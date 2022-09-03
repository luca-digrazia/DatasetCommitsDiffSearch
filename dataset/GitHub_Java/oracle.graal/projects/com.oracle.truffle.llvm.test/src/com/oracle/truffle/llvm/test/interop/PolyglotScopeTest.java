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
package com.oracle.truffle.llvm.test.interop;

import com.oracle.truffle.llvm.test.interop.values.BoxedTestValue;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.tck.TruffleRunner;
import com.oracle.truffle.tck.TruffleRunner.Inject;

@RunWith(TruffleRunner.class)
public class PolyglotScopeTest extends InteropTestBase {

    private static TruffleObject testLibrary;

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = InteropTestBase.loadTestBitcodeInternal("polyglotScopeTest");
    }

    public static class TestImportConstNode extends SulongTestNode {

        public TestImportConstNode() {
            super(testLibrary, "test_import_const", 0);
        }
    }

    @Test
    public void testImportConst(@Inject(TestImportConstNode.class) CallTarget testImport) {
        String value = "testImportConstValue";
        runWithPolyglot.getPolyglotContext().getPolyglotBindings().putMember("constName", value);
        Object ret = testImport.call();
        Assert.assertEquals(value, ret);
    }

    public static class TestImportVarNode extends SulongTestNode {

        public TestImportVarNode() {
            super(testLibrary, "test_import_var", 1);
        }
    }

    @Test
    public void testImportVar(@Inject(TestImportVarNode.class) CallTarget testImport) {
        String value = "testImportVarValue";
        runWithPolyglot.getPolyglotContext().getPolyglotBindings().putMember("varName", value);
        Object ret = testImport.call("varName");
        Assert.assertEquals(value, ret);
    }

    @Test
    public void testImportBoxed(@Inject(TestImportVarNode.class) CallTarget testImport) {
        String value = "testImportBoxedValue";
        runWithPolyglot.getPolyglotContext().getPolyglotBindings().putMember("boxedName", value);
        Object ret = testImport.call(new BoxedTestValue("boxedName"));
        Assert.assertEquals(value, ret);
    }

    public static class TestExportNode extends SulongTestNode {

        public TestExportNode() {
            super(testLibrary, "test_export", 1);
        }
    }

    @Test
    public void testExport(@Inject(TestExportNode.class) CallTarget testExport) {
        runWithPolyglot.getPolyglotContext().getPolyglotBindings().removeMember("exportName");
        testExport.call("exportName");
    }
}
