/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.wasm.utils.cases;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.function.BiConsumer;

import org.graalvm.wasm.utils.Assert;
import org.graalvm.wasm.utils.SystemProperties;
import org.graalvm.wasm.utils.WasmInitialization;
import org.graalvm.wasm.utils.WasmResource;
import org.graalvm.polyglot.Value;

/**
 * Instances of this class are used for WebAssembly test/benchmark cases.
 */
public abstract class WasmCase {
    private final String name;
    private final WasmCaseData data;
    private final WasmInitialization initialization;
    private final Properties options;

    public WasmCase(String name, WasmCaseData data, WasmInitialization initialization, Properties options) {
        this.name = name;
        this.data = data;
        this.initialization = initialization;
        this.options = options;
    }

    public String name() {
        return name;
    }

    public WasmCaseData data() {
        return data;
    }

    public WasmInitialization initialization() {
        return initialization;
    }

    public Properties options() {
        return options;
    }

    public abstract byte[] createBinary() throws IOException, InterruptedException;

    public static WasmStringCase create(String name, WasmCaseData data, String program) {
        return new WasmStringCase(name, data, program, null, new Properties());
    }

    public static WasmStringCase create(String name, WasmCaseData data, String program, WasmInitialization initializer, Properties options) {
        return new WasmStringCase(name, data, program, initializer, options);
    }

    public static WasmBinaryCase create(String name, WasmCaseData data, byte[] binary, WasmInitialization initializer, Properties options) {
        return new WasmBinaryCase(name, data, binary, initializer, options);
    }

    public static WasmCaseData expectedStdout(String expectedOutput) {
        return new WasmCaseData((Value result, String output) -> Assert.assertEquals("Failure: stdout:", expectedOutput, output));
    }

    public static WasmCaseData expected(Object expectedValue) {
        return new WasmCaseData((Value result, String output) -> Assert.assertEquals("Failure: result:", expectedValue, result.as(Object.class)));
    }

    public static WasmCaseData expectedFloat(float expectedValue, float delta) {
        return new WasmCaseData((Value result, String output) -> Assert.assertFloatEquals("Failure: result:", expectedValue, result.as(Float.class), delta));
    }

    public static WasmCaseData expectedDouble(double expectedValue, double delta) {
        return new WasmCaseData((Value result, String output) -> Assert.assertDoubleEquals("Failure: result:", expectedValue, result.as(Double.class), delta));
    }

    public static WasmCaseData expectedThrows(String expectedErrorMessage) {
        return new WasmCaseData(expectedErrorMessage);
    }

    public static Collection<WasmCase> collectFileCases(String type, String resource) throws IOException {
        Collection<WasmCase> collectedCases = new ArrayList<>();
        if (resource == null) {
            return collectedCases;
        }

        // Open the wasm_test_index file of the bundle. The wasm_test_index file contains the
        // available cases for that bundle.
        InputStream index = WasmCase.class.getResourceAsStream(String.format("/%s/%s/wasm_test_index", type, resource));
        BufferedReader indexReader = new BufferedReader(new InputStreamReader(index));

        // Iterate through the available test of the bundle.
        while (indexReader.ready()) {
            String caseName = indexReader.readLine().trim();

            if (caseName.equals("") || caseName.startsWith("#")) {
                // Skip empty lines or lines starting with a hash (treat as a comment).
                continue;
            }
            collectedCases.add(collectFileCase(type, resource, caseName));
        }

        return collectedCases;
    }

    public static WasmCase collectFileCase(String type, String resource, String caseName) throws IOException {
        Object mainContent = WasmResource.getResourceAsTest(String.format("/%s/%s/%s", type, resource, caseName), true);
        String resultContent = WasmResource.getResourceAsString(String.format("/%s/%s/%s.result", type, resource, caseName), true);
        String initContent = WasmResource.getResourceAsString(String.format("/%s/%s/%s.init", type, resource, caseName), false);
        String optsContent = WasmResource.getResourceAsString(String.format("/%s/%s/%s.opts", type, resource, caseName), false);
        WasmInitialization initializer = WasmInitialization.create(initContent);
        Properties options = SystemProperties.createFromOptions(optsContent);

        String[] resultTypeValue = resultContent.split("\\s+", 2);
        String resultType = resultTypeValue[0];
        String resultValue = resultTypeValue[1];

        WasmCaseData caseData = null;
        switch (resultType) {
            case "stdout":
                caseData = WasmCase.expectedStdout(resultValue);
                break;
            case "int":
                caseData = WasmCase.expected(Integer.parseInt(resultValue.trim()));
                break;
            case "long":
                caseData = WasmCase.expected(Long.parseLong(resultValue.trim()));
                break;
            case "float":
                caseData = WasmCase.expected(Float.parseFloat(resultValue.trim()));
                break;
            case "double":
                caseData = WasmCase.expected(Double.parseDouble(resultValue.trim()));
                break;
            case "exception":
                caseData = WasmCase.expectedThrows(resultValue);
                break;
            default:
                Assert.fail(String.format("Unknown type in result specification: %s", resultType));
        }

        if (mainContent instanceof String) {
            return WasmCase.create(caseName, caseData, (String) mainContent, initializer, options);
        } else if (mainContent instanceof byte[]) {
            return WasmCase.create(caseName, caseData, (byte[]) mainContent, initializer, options);
        } else {
            Assert.fail("Unknown content type: " + mainContent.getClass());
        }

        return null;
    }

    public static void validateResult(BiConsumer<Value, String> validator, Value result, OutputStream capturedStdout) {
        if (validator != null) {
            validator.accept(result, capturedStdout.toString());
        } else {
            Assert.fail("Test was not expected to return a value.");
        }
    }
}
