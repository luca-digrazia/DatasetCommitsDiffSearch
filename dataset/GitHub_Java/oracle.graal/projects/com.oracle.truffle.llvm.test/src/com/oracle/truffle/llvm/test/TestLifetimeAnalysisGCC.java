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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.Resource;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.intel.llvm.ireditor.lLVM_IR.BasicBlock;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.Instruction;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.llvm.parser.impl.LLVMWriteVisitor;
import com.oracle.truffle.llvm.parser.impl.lifetime.LLVMLifeTimeAnalysisResult;
import com.oracle.truffle.llvm.parser.impl.lifetime.LLVMLifeTimeAnalysisVisitor;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;
import com.oracle.truffle.llvm.test.FunctionVisitorIterator.LLVMFunctionVisitor;

@RunWith(Parameterized.class)
public class TestLifetimeAnalysisGCC extends TestSuiteBase {

    private static final String FUNCTION_INDENT = "";
    private static final String BEGIN_DEAD_END_INDENT = "\t";
    private static final String BASIC_BLOCK_INDENT = "\t\t";
    private static final String VARIABLE_INDENT = "\t\t\t";

    private static final String BEGIN_DEAD = BEGIN_DEAD_END_INDENT + "begin dead:";
    private static final String END_DEAD = BEGIN_DEAD_END_INDENT + "end dead:";

    private TestCaseFiles tuple;
    private File referenceOutputFile;
    private final PrintStream printStream;
    private BufferedReader referenceFileReader;
    private Map<String, LLVMLifeTimeAnalysisResult> referenceResults;

    public TestLifetimeAnalysisGCC(TestCaseFiles tuple) throws IOException {
        this.tuple = tuple;
        setUpReferenceFilePath(tuple);
        if (LLVMBaseOptionFacade.generateLifetimeReferenceOutput()) {
            referenceOutputFile.getParentFile().mkdirs();
            printStream = new PrintStream(referenceOutputFile);
        } else {
            printStream = null;
            FileInputStream fis = new FileInputStream(referenceOutputFile);
            referenceFileReader = new BufferedReader(new InputStreamReader(fis));
            referenceResults = parseReferenceResults();
        }
    }

    private void setUpReferenceFilePath(TestCaseFiles tuple) {
        String referenceFileRelative = tuple.getOriginalFile().getAbsolutePath().substring(LLVMPaths.GCC_TEST_SUITE.getAbsolutePath().length() + 1) + ".lifetime";
        referenceOutputFile = new File(LLVMPaths.LIFETIME_ANALYSIS_REFERENCE_FILES, referenceFileRelative);
    }

    @Parameterized.Parameters
    public static List<TestCaseFiles[]> getTestFiles() throws IOException {
        File configFile = LLVMPaths.GCC_TEST_SUITE_CONFIG;
        File testSuite = LLVMPaths.GCC_TEST_SUITE;
        List<TestCaseFiles[]> files = getTestCasesFromConfigFile(configFile, testSuite, new TestCaseGeneratorImpl(false));
        return files;
    }

    private enum ParseState {
        LOOKING_FOR_FUNCTION,
        LOOKING_FOR_BEGIN_DEAD,
        LOOKING_FOR_BLOCK_OR_END_DEAD,
        LOOKING_FOR_BLOCK_OR_END,
        LOOKING_FOR_VARIABLE_BEGIN,
        LOOKING_FOR_VARIABLE_END;
    }

    @Test
    public void test() throws Throwable {
        try {
            LLVMLogger.info("original file: " + tuple.getOriginalFile());

            FunctionVisitorIterator.visitFunctions(new LLVMFunctionVisitor() {

                @Override
                public void visit(FunctionDef def) {
                    Set<String> writes = LLVMWriteVisitor.visit(def);
                    FrameDescriptor frameDescriptor = new FrameDescriptor();
                    for (String variableName : writes) {
                        frameDescriptor.findOrAddFrameSlot(variableName);
                    }
                    LLVMLifeTimeAnalysisResult analysisResult = LLVMLifeTimeAnalysisVisitor.visit(def,
                                    frameDescriptor);
                    String functionName = def.getHeader().getName();
                    if (LLVMBaseOptionFacade.generateLifetimeReferenceOutput()) {
                        printStringln(functionName);
                        printStringln(BEGIN_DEAD);
                        Map<BasicBlock, FrameSlot[]> beginDead = analysisResult.getBeginDead();
                        printBasicBlockVariables(beginDead);
                        printStringln(END_DEAD);
                        Map<BasicBlock, FrameSlot[]> endDead = analysisResult.getEndDead();
                        printBasicBlockVariables(endDead);
                    } else {
                        LLVMLifeTimeAnalysisResult expected = referenceResults.get(functionName);
                        Assert.assertEquals(functionName, expected, analysisResult);
                    }
                }

            }, tuple.getBitCodeFile());
        } catch (Throwable e) {
            recordError(tuple, e);
            throw e;
        }
    }

    private static BasicBlock createBasicBlock(String name) {
        return new BasicBlock() {

            @Override
            public void eSetDeliver(boolean arg0) {
            }

            @Override
            public void eNotify(Notification arg0) {
            }

            @Override
            public boolean eDeliver() {
                return false;
            }

            @Override
            public EList<Adapter> eAdapters() {
                return null;
            }

            @Override
            public void eUnset(EStructuralFeature arg0) {
            }

            @Override
            public void eSet(EStructuralFeature arg0, Object arg1) {
            }

            @Override
            public Resource eResource() {
                return null;
            }

            @Override
            public boolean eIsSet(EStructuralFeature arg0) {
                return false;
            }

            @Override
            public boolean eIsProxy() {
                return false;
            }

            @Override
            public Object eInvoke(EOperation arg0, EList<?> arg1) throws InvocationTargetException {
                return null;
            }

            @Override
            public Object eGet(EStructuralFeature arg0, boolean arg1) {
                return null;
            }

            @Override
            public Object eGet(EStructuralFeature arg0) {
                return null;
            }

            @Override
            public EList<EObject> eCrossReferences() {
                return null;
            }

            @Override
            public EList<EObject> eContents() {
                return null;
            }

            @Override
            public EReference eContainmentFeature() {
                return null;
            }

            @Override
            public EStructuralFeature eContainingFeature() {
                return null;
            }

            @Override
            public EObject eContainer() {
                return null;
            }

            @Override
            public EClass eClass() {
                return null;
            }

            @Override
            public TreeIterator<EObject> eAllContents() {
                return null;
            }

            @Override
            public void setName(String arg0) {
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public EList<Instruction> getInstructions() {
                return null;
            }
        };
    }

    private Map<String, LLVMLifeTimeAnalysisResult> parseReferenceResults() throws IOException {
        ParseState state = ParseState.LOOKING_FOR_FUNCTION;
        boolean deadAtBeginning = true;
        String block = null;
        FrameDescriptor descr = new FrameDescriptor();
        Map<BasicBlock, FrameSlot[]> beginDead = new HashMap<>();
        Map<BasicBlock, FrameSlot[]> endDead = new HashMap<>();
        Map<String, LLVMLifeTimeAnalysisResult> results = new HashMap<>();
        String functionName = null;
        Set<FrameSlot> slots = new HashSet<>();
        while (referenceFileReader.ready()) {
            String line = referenceFileReader.readLine();
            switch (state) {
                case LOOKING_FOR_FUNCTION:
                    if (line.startsWith(FUNCTION_INDENT)) {
                        state = ParseState.LOOKING_FOR_BEGIN_DEAD;
                        functionName = line;
                    } else {
                        throw new AssertionError(line);
                    }
                    break;
                case LOOKING_FOR_BEGIN_DEAD:
                    if (line.equals(BEGIN_DEAD)) {
                        state = ParseState.LOOKING_FOR_BLOCK_OR_END_DEAD;
                        deadAtBeginning = true;
                    } else {
                        throw new AssertionError(line);
                    }
                    break;
                case LOOKING_FOR_BLOCK_OR_END_DEAD:
                    if (line.equals(END_DEAD)) {
                        deadAtBeginning = false;
                        state = ParseState.LOOKING_FOR_BLOCK_OR_END;
                    } else if (line.startsWith(BASIC_BLOCK_INDENT)) {
                        block = line.substring(BASIC_BLOCK_INDENT.length());
                        state = ParseState.LOOKING_FOR_VARIABLE_BEGIN;
                    } else {
                        throw new AssertionError(line);
                    }
                    break;
                case LOOKING_FOR_VARIABLE_BEGIN:
                    if (line.startsWith(VARIABLE_INDENT)) {
                        String variableName = line.substring(VARIABLE_INDENT.length());
                        slots.add(descr.findOrAddFrameSlot(variableName));
                    } else if (line.startsWith(BASIC_BLOCK_INDENT)) {
                        finishEntry(deadAtBeginning, block, beginDead, endDead, slots);
                        block = line.substring(BASIC_BLOCK_INDENT.length());
                        state = ParseState.LOOKING_FOR_VARIABLE_BEGIN;
                    } else if (line.startsWith(END_DEAD)) {
                        finishEntry(deadAtBeginning, block, beginDead, endDead, slots);
                        deadAtBeginning = false;
                        state = ParseState.LOOKING_FOR_BLOCK_OR_END;
                    } else {
                        throw new AssertionError(line);
                    }
                    break;
                case LOOKING_FOR_BLOCK_OR_END:
                    if (line.startsWith(BASIC_BLOCK_INDENT)) {
                        block = line.substring(BASIC_BLOCK_INDENT.length());
                        state = ParseState.LOOKING_FOR_VARIABLE_END;
                    } else if (line.startsWith(FUNCTION_INDENT)) {
                        finishEntry(deadAtBeginning, block, beginDead, endDead, slots);
                        results.put(functionName, result(beginDead, endDead));
                        beginDead = new HashMap<>();
                        endDead = new HashMap<>();
                    } else {
                        throw new AssertionError(line);
                    }
                    break;
                case LOOKING_FOR_VARIABLE_END:
                    if (line.startsWith(VARIABLE_INDENT)) {
                        String variableName = line.substring(VARIABLE_INDENT.length());
                        slots.add(descr.findOrAddFrameSlot(variableName));
                    } else if (line.startsWith(BASIC_BLOCK_INDENT)) {
                        finishEntry(deadAtBeginning, block, beginDead, endDead, slots);
                        block = line.substring(BASIC_BLOCK_INDENT.length());
                        state = ParseState.LOOKING_FOR_VARIABLE_END;
                    } else if (line.startsWith(FUNCTION_INDENT)) {
                        finishEntry(deadAtBeginning, block, beginDead, endDead, slots);
                        results.put(functionName, result(beginDead, endDead));
                        functionName = line;
                        beginDead = new HashMap<>();
                        endDead = new HashMap<>();
                        state = ParseState.LOOKING_FOR_BEGIN_DEAD;
                    } else {
                        throw new AssertionError(line);
                    }
                    break;
                default:
                    throw new AssertionError();
            }
        }
        finishEntry(deadAtBeginning, block, beginDead, endDead, slots);
        results.put(functionName, result(beginDead, endDead));
        return results;
    }

    private static LLVMLifeTimeAnalysisResult result(Map<BasicBlock, FrameSlot[]> beginDead, Map<BasicBlock, FrameSlot[]> endDead) {
        return new LLVMLifeTimeAnalysisResult() {

            @Override
            public Map<BasicBlock, FrameSlot[]> getEndDead() {
                return endDead;
            }

            @Override
            public Map<BasicBlock, FrameSlot[]> getBeginDead() {
                return beginDead;
            }
        };
    }

    private static void finishEntry(boolean deadAtBeginning, String block, Map<BasicBlock, FrameSlot[]> beginDead, Map<BasicBlock, FrameSlot[]> endDead, Set<FrameSlot> slots) {
        if (deadAtBeginning) {
            beginDead.put(createBasicBlock(block), slots.toArray(new FrameSlot[slots.size()]));
        } else {
            endDead.put(createBasicBlock(block), slots.toArray(new FrameSlot[slots.size()]));
        }
        slots.clear();
    }

    private void printBasicBlockVariables(Map<BasicBlock, FrameSlot[]> beginDead) {
        for (BasicBlock b : beginDead.keySet()) {
            printString(BASIC_BLOCK_INDENT + b.getName());
            for (FrameSlot slot : beginDead.get(b)) {
                if (slot != null) {
                    printString(VARIABLE_INDENT + slot.getIdentifier());
                }
            }
        }
    }

    void printStringln(String s) {
        printStream.println(s);
    }

    void printString(String s) {
        printStream.println(s);
    }

}
