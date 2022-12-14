/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.test;

import java.io.File;

import com.oracle.truffle.llvm.tools.util.PathUtil;

final class TestCaseFiles {

    private final File originalFile;
    private final File bitCodeFile;
    private final File expectedResult;

    private TestCaseFiles(File bitCodeFile) {
        this(bitCodeFile, bitCodeFile, bitCodeFile);
    }

    private TestCaseFiles(File originalFile, File byteCodeFile) {
        this(originalFile, byteCodeFile, null);
    }

    private TestCaseFiles(File originalFile, File bitCodeFile, File expectedResult) {
        checkBitCodeFile(bitCodeFile);
        this.originalFile = originalFile;
        this.bitCodeFile = bitCodeFile;
        this.expectedResult = expectedResult;
    }

    public File getOriginalFile() {
        return originalFile;
    }

    public File getBitCodeFile() {
        return bitCodeFile;
    }

    public File getExpectedResult() {
        return expectedResult;
    }

    public static TestCaseFiles createFromCompiledFile(File originalFile, File bitCodeFile, File expectedResult) {
        return new TestCaseFiles(originalFile, bitCodeFile, expectedResult);
    }

    public static TestCaseFiles createFromCompiledFile(File originalFile, File bitCodeFile) {
        return new TestCaseFiles(originalFile, bitCodeFile);
    }

    public static TestCaseFiles createFromBitCodeFile(File bitCodeFile) {
        return new TestCaseFiles(bitCodeFile);
    }

    public static TestCaseFiles createFromBitCodeFile(File bitCodeFile, File expectedResult) {
        return new TestCaseFiles(bitCodeFile, bitCodeFile, expectedResult);
    }

    private static void checkBitCodeFile(File bitCodeFile) {
        assert bitCodeFile != null;
        String extension = PathUtil.getExtension(bitCodeFile.getName());
        if (!Constants.LLVM_BITFILE_EXTENSION.equals(extension)) {
            throw new IllegalArgumentException(bitCodeFile + " is not a bitcode file!");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TestCaseFiles)) {
            return false;
        } else {
            TestCaseFiles other = (TestCaseFiles) obj;
            return bitCodeFile.equals(other.bitCodeFile) && originalFile.equals(other.originalFile) &&
                            (expectedResult == null && other.expectedResult == null || expectedResult.equals(other.expectedResult));
        }
    }

    @Override
    public int hashCode() {
        return originalFile.hashCode() + bitCodeFile.hashCode() + expectedResult.hashCode();
    }

    @Override
    public String toString() {
        return "original file: " + originalFile + " bitcode file: " + bitCodeFile + " expected result: " + expectedResult;
    }

}
