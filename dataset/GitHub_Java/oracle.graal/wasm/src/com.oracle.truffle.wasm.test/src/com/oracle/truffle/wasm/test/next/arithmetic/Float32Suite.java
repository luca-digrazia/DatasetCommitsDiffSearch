/*
 *  Copyright (c) 2019, Oracle and/or its affiliates.
 *
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without modification, are
 *  permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice, this list of
 *  conditions and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice, this list of
 *  conditions and the following disclaimer in the documentation and/or other materials provided
 *  with the distribution.
 *
 *  3. Neither the name of the copyright holder nor the names of its contributors may be used to
 *  endorse or promote products derived from this software without specific prior written
 *  permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 *  OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 *  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *  COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 *  GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 *  AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 *  OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.wasm.test.next.arithmetic;

import java.util.Arrays;
import java.util.Collection;

import com.oracle.truffle.wasm.test.next.WasmSuiteBase;

public class Float32Suite extends WasmSuiteBase {
    private WasmStringTestCase[] testCases = {
            testCase("EQ_TRUE", expected(1),
                        "(module (func (result i32) f32.const 0.5 f32.const 0.5 f32.eq))"),
            testCase("EQ_FALSE", expected(0),
                        "(module (func (result i32) f32.const 0.5 f32.const 0.1 f32.eq))"),
            testCase("EQ_TRUE_NEG_ZEROES", expected(1),
                        "(module (func (result i32) f32.const -0.0 f32.const 0.0 f32.eq))"),
            testCase("EQ_FALSE_NAN", expected(0),
                        "(module (func (result i32) f32.const nan f32.const nan f32.eq))"),
            testCase("NE_FALSE", expected(0),
                        "(module (func (result i32) f32.const 0.5 f32.const 0.5 f32.ne))"),
            testCase("NE_TRUE", expected(1),
                        "(module (func (result i32) f32.const 0.5 f32.const 0.1 f32.ne))"),
            testCase("NE_FALSE_NEG_ZEROES", expected(0),
                        "(module (func (result i32) f32.const -0.0 f32.const 0.0 f32.ne))"),
            testCase("NE_TRUE_NAN", expected(1),
                        "(module (func (result i32) f32.const nan f32.const nan f32.ne))"),
            testCase("LT_FALSE_BOTH_MINUS_INF", expected(0),
                        "(module (func (result i32) f32.const -inf f32.const -inf f32.lt))"),
            testCase("LT_FALSE_BOTH_PLUS_INF", expected(0),
                        "(module (func (result i32) f32.const inf f32.const inf f32.lt))"),
            testCase("LT_TRUE_MINUS_INF", expected(1),
                        "(module (func (result i32) f32.const -inf f32.const -1 f32.lt))"),
            testCase("LT_TRUE_INF", expected(1),
                        "(module (func (result i32) f32.const -inf f32.const inf f32.lt))"),
            testCase("LT_FALSE_INF_NAN", expected(0),
                        "(module (func (result i32) f32.const -inf f32.const nan f32.lt))"),
            testCase("LT_FALSE_BOTH_NAN", expected(0),
                        "(module (func (result i32) f32.const nan f32.const nan f32.lt))"),
            testCase("LT_FALSE_EQUAL", expected(0),
                        "(module (func (result i32) f32.const 1.0 f32.const 1.0 f32.lt))"),
            testCase("LT_FALSE", expected(0),
                        "(module (func (result i32) f32.const 2.0 f32.const 1.0 f32.lt))"),
            testCase("LT_TRUE", expected(1),
                        "(module (func (result i32) f32.const 1.0 f32.const 1.5 f32.lt))"),
            testCase("GT_FALSE_BOTH_MINUS_INF", expected(0),
                        "(module (func (result i32) f32.const -inf f32.const -inf f32.gt))"),
            testCase("GT_FALSE_BOTH_PLUS_INF", expected(0),
                        "(module (func (result i32) f32.const inf f32.const inf f32.gt))"),
            testCase("GT_FALSE_MINUS_INF", expected(0),
                        "(module (func (result i32) f32.const -inf f32.const -1 f32.gt))"),
            testCase("GT_TRUE_INF", expected(1),
                        "(module (func (result i32) f32.const inf f32.const -inf f32.gt))"),
            testCase("GT_FALSE_INF_NAN", expected(0),
                        "(module (func (result i32) f32.const inf f32.const nan f32.gt))"),
            testCase("GT_FALSE_BOTH_NAN", expected(0),
                        "(module (func (result i32) f32.const nan f32.const nan f32.gt))"),
            testCase("GT_FALSE_EQUAL", expected(0),
                        "(module (func (result i32) f32.const 1.0 f32.const 1.0 f32.gt))"),
            testCase("GT_TRUE", expected(1),
                        "(module (func (result i32) f32.const 2.0 f32.const 1.5 f32.gt))"),
            testCase("GT_FALSE", expected(0),
                        "(module (func (result i32) f32.const 1.0 f32.const 1.5 f32.gt))"),
            testCase("LE_TRUE_BOTH_MINUS_INF", expected(1),
                        "(module (func (result i32) f32.const -inf f32.const -inf f32.le))"),
            testCase("LE_TRUE_BOTH_PLUS_INF", expected(1),
                        "(module (func (result i32) f32.const inf f32.const inf f32.le))"),
            testCase("LE_TRUE_MINUS_INF", expected(1),
                        "(module (func (result i32) f32.const -inf f32.const -1 f32.le))"),
            testCase("LE_TRUE_INF", expected(1),
                        "(module (func (result i32) f32.const -inf f32.const inf f32.le))"),
            testCase("LE_FALSE_INF_NAN", expected(0),
                        "(module (func (result i32) f32.const -inf f32.const nan f32.le))"),
            testCase("LE_FALSE_BOTH_NAN", expected(0),
                        "(module (func (result i32) f32.const nan f32.const nan f32.le))"),
            testCase("LE_TRUE_EQUAL", expected(1),
                        "(module (func (result i32) f32.const 1.0 f32.const 1.0 f32.le))"),
            testCase("LE_FALSE", expected(0),
                        "(module (func (result i32) f32.const 2.0 f32.const 1.0 f32.le))"),
            testCase("LE_TRUE", expected(1),
                        "(module (func (result i32) f32.const 1.0 f32.const 1.5 f32.le))"),
            testCase("GE_TRUE_BOTH_MINUS_INF", expected(1),
                        "(module (func (result i32) f32.const -inf f32.const -inf f32.ge))"),
            testCase("GE_TRUE_BOTH_PLUS_INF", expected(1),
                        "(module (func (result i32) f32.const inf f32.const inf f32.ge))"),
            testCase("GE_FALSE_MINUS_INF", expected(0),
                        "(module (func (result i32) f32.const -inf f32.const -1 f32.ge))"),
            testCase("GE_TRUE_INF", expected(1),
                        "(module (func (result i32) f32.const inf f32.const -inf f32.ge))"),
            testCase("GE_FALSE_INF_NAN", expected(0),
                        "(module (func (result i32) f32.const inf f32.const nan f32.ge))"),
            testCase("GE_FALSE_BOTH_NAN", expected(0),
                        "(module (func (result i32) f32.const nan f32.const nan f32.ge))"),
            testCase("GE_TRUE_EQUAL", expected(1),
                        "(module (func (result i32) f32.const 1.0 f32.const 1.0 f32.ge))"),
            testCase("GE_TRUE", expected(1),
                        "(module (func (result i32) f32.const 2.0 f32.const 1.5 f32.ge))"),
            testCase("GE_FALSE", expected(0),
                        "(module (func (result i32) f32.const 1.0 f32.const 1.5 f32.ge))"),
    };

    @Override
    protected Collection<? extends WasmTestCase> collectTestCases() {
        return Arrays.asList(testCases);
    }
}
