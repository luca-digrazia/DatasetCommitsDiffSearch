/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.bytecode;

import static com.oracle.graal.bytecode.Bytecodes.ALOAD;
import static com.oracle.graal.bytecode.Bytecodes.ANEWARRAY;
import static com.oracle.graal.bytecode.Bytecodes.ASTORE;
import static com.oracle.graal.bytecode.Bytecodes.BIPUSH;
import static com.oracle.graal.bytecode.Bytecodes.CHECKCAST;
import static com.oracle.graal.bytecode.Bytecodes.DLOAD;
import static com.oracle.graal.bytecode.Bytecodes.DSTORE;
import static com.oracle.graal.bytecode.Bytecodes.FLOAD;
import static com.oracle.graal.bytecode.Bytecodes.FSTORE;
import static com.oracle.graal.bytecode.Bytecodes.GETFIELD;
import static com.oracle.graal.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.graal.bytecode.Bytecodes.GOTO;
import static com.oracle.graal.bytecode.Bytecodes.GOTO_W;
import static com.oracle.graal.bytecode.Bytecodes.IFEQ;
import static com.oracle.graal.bytecode.Bytecodes.IFGE;
import static com.oracle.graal.bytecode.Bytecodes.IFGT;
import static com.oracle.graal.bytecode.Bytecodes.IFLE;
import static com.oracle.graal.bytecode.Bytecodes.IFLT;
import static com.oracle.graal.bytecode.Bytecodes.IFNE;
import static com.oracle.graal.bytecode.Bytecodes.IFNONNULL;
import static com.oracle.graal.bytecode.Bytecodes.IFNULL;
import static com.oracle.graal.bytecode.Bytecodes.IF_ACMPEQ;
import static com.oracle.graal.bytecode.Bytecodes.IF_ACMPNE;
import static com.oracle.graal.bytecode.Bytecodes.IF_ICMPEQ;
import static com.oracle.graal.bytecode.Bytecodes.IF_ICMPGE;
import static com.oracle.graal.bytecode.Bytecodes.IF_ICMPGT;
import static com.oracle.graal.bytecode.Bytecodes.IF_ICMPLE;
import static com.oracle.graal.bytecode.Bytecodes.IF_ICMPLT;
import static com.oracle.graal.bytecode.Bytecodes.IF_ICMPNE;
import static com.oracle.graal.bytecode.Bytecodes.ILOAD;
import static com.oracle.graal.bytecode.Bytecodes.INSTANCEOF;
import static com.oracle.graal.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.graal.bytecode.Bytecodes.INVOKEINTERFACE;
import static com.oracle.graal.bytecode.Bytecodes.INVOKESPECIAL;
import static com.oracle.graal.bytecode.Bytecodes.INVOKESTATIC;
import static com.oracle.graal.bytecode.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.graal.bytecode.Bytecodes.ISTORE;
import static com.oracle.graal.bytecode.Bytecodes.JSR;
import static com.oracle.graal.bytecode.Bytecodes.JSR_W;
import static com.oracle.graal.bytecode.Bytecodes.LDC;
import static com.oracle.graal.bytecode.Bytecodes.LDC2_W;
import static com.oracle.graal.bytecode.Bytecodes.LDC_W;
import static com.oracle.graal.bytecode.Bytecodes.LLOAD;
import static com.oracle.graal.bytecode.Bytecodes.LOOKUPSWITCH;
import static com.oracle.graal.bytecode.Bytecodes.LSTORE;
import static com.oracle.graal.bytecode.Bytecodes.MULTIANEWARRAY;
import static com.oracle.graal.bytecode.Bytecodes.NEW;
import static com.oracle.graal.bytecode.Bytecodes.NEWARRAY;
import static com.oracle.graal.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.graal.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.graal.bytecode.Bytecodes.RET;
import static com.oracle.graal.bytecode.Bytecodes.SIPUSH;
import static com.oracle.graal.bytecode.Bytecodes.TABLESWITCH;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Utility for producing a {@code javap}-like disassembly of bytecode.
 */
public class BytecodeDisassembler {

    /**
     * Specifies if the disassembly for a single instruction can span multiple lines.
     */
    private final boolean multiline;

    private final boolean newLine;

    public BytecodeDisassembler(boolean multiline, boolean newLine) {
        this.multiline = multiline;
        this.newLine = newLine;
    }

    public BytecodeDisassembler(boolean multiline) {
        this(multiline, true);
    }

    public BytecodeDisassembler() {
        this(true, true);
    }

    public static String disassembleOne(ResolvedJavaMethod method, int bci) {
        return new BytecodeDisassembler(false, false).disassemble(method, bci, bci);
    }

    /**
     * Disassembles the bytecode of a given method in a {@code javap}-like format.
     *
     * @return {@code null} if {@code method} has no bytecode (e.g., it is native or abstract)
     */
    public String disassemble(ResolvedJavaMethod method) {
        return disassemble(method, 0, Integer.MAX_VALUE);
    }

    /**
     * Disassembles the bytecode of a given method in a {@code javap}-like format.
     *
     * @return {@code null} if {@code method} has no bytecode (e.g., it is native or abstract)
     */
    public String disassemble(ResolvedJavaMethod method, int startBci, int endBci) {
        return disassemble(new ResolvedJavaMethodBytecode(method), startBci, endBci);
    }

    /**
     * Disassembles {@code code} in a {@code javap}-like format.
     */
    public String disassemble(Bytecode code) {
        return disassemble(code, 0, Integer.MAX_VALUE);
    }

    /**
     * Disassembles {@code code} in a {@code javap}-like format.
     */
    public String disassemble(Bytecode code, int startBci, int endBci) {
        if (code.getCode() == null) {
            return null;
        }
        ResolvedJavaMethod method = code.getMethod();
        ConstantPool cp = code.getConstantPool();
        BytecodeStream stream = new BytecodeStream(code.getCode());
        StringBuilder buf = new StringBuilder();
        int opcode = stream.currentBC();
        while (opcode != Bytecodes.END) {
            int bci = stream.currentBCI();
            if (bci >= startBci && bci <= endBci) {
                String mnemonic = Bytecodes.nameOf(opcode);
                buf.append(String.format("%4d: %-14s", bci, mnemonic));
                if (stream.nextBCI() > bci + 1) {
                    // @formatter:off
                switch (opcode) {
                    case BIPUSH         : buf.append(stream.readByte()); break;
                    case SIPUSH         : buf.append(stream.readShort()); break;
                    case NEW            :
                    case CHECKCAST      :
                    case INSTANCEOF     :
                    case ANEWARRAY      : {
                        int cpi = stream.readCPI();
                        JavaType type = cp.lookupType(cpi, opcode);
                        buf.append(String.format("#%-10d // %s", cpi, type.toJavaName()));
                        break;
                    }
                    case GETSTATIC      :
                    case PUTSTATIC      :
                    case GETFIELD       :
                    case PUTFIELD       : {
                        int cpi = stream.readCPI();
                        JavaField field = cp.lookupField(cpi, method, opcode);
                        String fieldDesc = field.getDeclaringClass().getName().equals(method.getDeclaringClass().getName()) ? field.format("%n:%T") : field.format("%H.%n:%T");
                        buf.append(String.format("#%-10d // %s", cpi, fieldDesc));
                        break;
                    }
                    case INVOKEVIRTUAL  :
                    case INVOKESPECIAL  :
                    case INVOKESTATIC   : {
                        int cpi = stream.readCPI();
                        JavaMethod callee = cp.lookupMethod(cpi, opcode);
                        String calleeDesc = callee.getDeclaringClass().getName().equals(method.getDeclaringClass().getName()) ? callee.format("%n:(%P)%R") : callee.format("%H.%n:(%P)%R");
                        buf.append(String.format("#%-10d // %s", cpi, calleeDesc));
                        break;
                    }
                    case INVOKEINTERFACE: {
                        int cpi = stream.readCPI();
                        JavaMethod callee = cp.lookupMethod(cpi, opcode);
                        String calleeDesc = callee.getDeclaringClass().getName().equals(method.getDeclaringClass().getName()) ? callee.format("%n:(%P)%R") : callee.format("%H.%n:(%P)%R");
                        buf.append(String.format("#%-10s // %s", cpi + ", " + stream.readUByte(bci + 3), calleeDesc));
                        break;
                    }
                    case INVOKEDYNAMIC: {
                        int cpi = stream.readCPI4();
                        JavaMethod callee = cp.lookupMethod(cpi, opcode);
                        String calleeDesc = callee.getDeclaringClass().getName().equals(method.getDeclaringClass().getName()) ? callee.format("%n:(%P)%R") : callee.format("%H.%n:(%P)%R");
                        buf.append(String.format("#%-10d // %s", cpi, calleeDesc));
                        break;
                    }
                    case LDC            :
                    case LDC_W          :
                    case LDC2_W         : {
                        int cpi = stream.readCPI();
                        Object constant = cp.lookupConstant(cpi);
                        String desc = null;
                        if (constant instanceof JavaConstant) {
                            JavaConstant c = ((JavaConstant) constant);
                            desc = c.toValueString();
                        } else {
                            desc = constant.toString();
                        }
                        if (!multiline) {
                            desc = desc.replaceAll("\\n", "");
                        }
                        buf.append(String.format("#%-10d // %s", cpi, desc));
                        break;
                    }
                    case RET            :
                    case ILOAD          :
                    case LLOAD          :
                    case FLOAD          :
                    case DLOAD          :
                    case ALOAD          :
                    case ISTORE         :
                    case LSTORE         :
                    case FSTORE         :
                    case DSTORE         :
                    case ASTORE         : {
                        buf.append(String.format("%d", stream.readLocalIndex()));
                        break;
                    }
                    case IFEQ           :
                    case IFNE           :
                    case IFLT           :
                    case IFGE           :
                    case IFGT           :
                    case IFLE           :
                    case IF_ICMPEQ      :
                    case IF_ICMPNE      :
                    case IF_ICMPLT      :
                    case IF_ICMPGE      :
                    case IF_ICMPGT      :
                    case IF_ICMPLE      :
                    case IF_ACMPEQ      :
                    case IF_ACMPNE      :
                    case GOTO           :
                    case JSR            :
                    case IFNULL         :
                    case IFNONNULL      :
                    case GOTO_W         :
                    case JSR_W          : {
                        buf.append(String.format("%d", stream.readBranchDest()));
                        break;
                    }
                    case LOOKUPSWITCH   :
                    case TABLESWITCH    : {
                        BytecodeSwitch bswitch = opcode == LOOKUPSWITCH ? new BytecodeLookupSwitch(stream, bci) : new BytecodeTableSwitch(stream, bci);
                        if (multiline) {
                            buf.append("{ // " + bswitch.numberOfCases());
                            for (int i = 0; i < bswitch.numberOfCases(); i++) {
                                buf.append(String.format("%n           %7d: %d", bswitch.keyAt(i), bswitch.targetAt(i)));
                            }
                            buf.append(String.format("%n           default: %d", bswitch.defaultTarget()));
                            buf.append(String.format("%n      }"));
                        } else {
                            buf.append("[" + bswitch.numberOfCases()).append("] {");
                            for (int i = 0; i < bswitch.numberOfCases(); i++) {
                                buf.append(String.format("%d: %d", bswitch.keyAt(i), bswitch.targetAt(i)));
                                if (i != bswitch.numberOfCases() - 1) {
                                    buf.append(", ");
                                }
                            }
                            buf.append(String.format("} default: %d", bswitch.defaultTarget()));
                        }
                        break;
                    }
                    case NEWARRAY       : {
                        int typecode = stream.readLocalIndex();
                        // Checkstyle: stop
                        switch (typecode) {
                            case 4:  buf.append("boolean"); break;
                            case 5:  buf.append("char"); break;
                            case 6:  buf.append("float"); break;
                            case 7:  buf.append("double"); break;
                            case 8:  buf.append("byte"); break;
                            case 9:  buf.append("short"); break;
                            case 10: buf.append("int"); break;
                            case 11: buf.append("long"); break;
                        }
                        // Checkstyle: resume

                        break;
                    }
                    case MULTIANEWARRAY : {
                        int cpi = stream.readCPI();
                        JavaType type = cp.lookupType(cpi, opcode);
                        buf.append(String.format("#%-10s // %s", cpi + ", " + stream.readUByte(bci + 3), type.toJavaName()));
                        break;
                    }
                }
                // @formatter:on
                }
                if (newLine) {
                    buf.append(String.format("%n"));
                }
            }
            stream.next();
            opcode = stream.currentBC();
        }
        return buf.toString();
    }
}
