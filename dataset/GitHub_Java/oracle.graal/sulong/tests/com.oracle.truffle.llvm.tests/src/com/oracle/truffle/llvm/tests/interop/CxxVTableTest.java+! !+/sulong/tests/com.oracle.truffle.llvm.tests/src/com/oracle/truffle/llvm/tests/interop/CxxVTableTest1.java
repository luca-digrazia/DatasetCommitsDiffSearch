/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.interop;

import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.tck.TruffleRunner;

@RunWith(TruffleRunner.class)
public class CxxVTableTest extends InteropTestBase {

    private static Value testCppLibrary;
    private static Value evaluateDirectly;
    private static Value evaluateWithPolyglotConversion;
    private static Value getB1F;
    private static Value getB1G;

    @BeforeClass
    public static void loadTestBitcode() {
        testCppLibrary = loadTestBitcodeValue("vtableTest.cpp");
        evaluateDirectly = testCppLibrary.getMember("evaluateDirectly");
        evaluateWithPolyglotConversion = testCppLibrary.getMember("evaluateWithPolyglotConversion");
        getB1F = testCppLibrary.getMember("getB1F");
        getB1G = testCppLibrary.getMember("getB1G");
    }

    @Test
    public void testWithoutPolyglot() {
        Assert.assertEquals(0, getB1G.execute().asInt());
        Assert.assertEquals(2, getB1F.execute().asInt());
    }

    @Test
    public void testWithPolyglot() {
        CxxVtableTest_TestClass testObject = new CxxVtableTest_TestClass();
        Assert.assertEquals(50, evaluateDirectly.execute(testObject, 10).asInt());
        Assert.assertEquals(60, evaluateDirectly.execute(testObject, 10).asInt());
        Assert.assertEquals(10, evaluateDirectly.execute(testObject, 0).asInt());
        /*
         * testObject stores last argument and uses it at the next call. Therefore, calls with the
         * same parameters lead to different results.
         */
        Assert.assertEquals(50, evaluateWithPolyglotConversion.execute(testObject, 10).asInt());
        Assert.assertEquals(60, evaluateWithPolyglotConversion.execute(testObject, 10).asInt());
        Assert.assertEquals(10, evaluateWithPolyglotConversion.execute(testObject, 0).asInt());
    }
}
