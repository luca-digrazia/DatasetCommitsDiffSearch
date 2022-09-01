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

// The content of this file is automatically generated. DO NOT EDIT.

package com.oracle.truffle.llvm.asm.amd64;

import com.oracle.truffle.llvm.nodes.base.LLVMExpressionNode;
import com.oracle.truffle.llvm.nodes.impl.base.integers.LLVMI32Node;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMArgNodeFactory.LLVMI32ArgNodeGen;
import com.oracle.truffle.llvm.nodes.impl.func.LLVMInlineAssemblyRootNode;
import com.oracle.truffle.llvm.parser.LLVMBaseType;
import java.io.ByteArrayInputStream;

// Checkstyle: stop
// @formatter:off
public class Parser {
	public static final int _EOF = 0;
	public static final int _ident = 1;
	public static final int _number = 2;
	public static final int _hexNumber = 3;
	public static final int maxT = 28;

	static final boolean _T = true;
	static final boolean _x = false;
	static final int minErrDist = 2;

	public Token t;    // last recognized token
	public Token la;   // lookahead token
	int errDist = minErrDist;
	
	public final Scanner scanner;
	public final Errors errors;
	private final AsmNodeFactory factory;
	private LLVMInlineAssemblyRootNode root;
	

	public Parser(String asmSnippet, @SuppressWarnings("unused") String asmFlags, @SuppressWarnings("unused") LLVMExpressionNode[] args, @SuppressWarnings("unused") LLVMBaseType retType) {
		this.scanner = new Scanner(new ByteArrayInputStream(asmSnippet.getBytes()));
		errors = new Errors();
		this.factory = new AsmNodeFactory();
	}

	void SynErr (int n) {
		if (errDist >= minErrDist) errors.SynErr(la.line, la.col, n);
		errDist = 0;
	}

	public void SemErr (String msg) {
		if (errDist >= minErrDist) errors.SemErr(t.line, t.col, msg);
		errDist = 0;
	}
	
	void Get () {
		for (;;) {
			t = la;
			la = scanner.Scan();
			if (la.kind <= maxT) {
				++errDist;
				break;
			}

			la = t;
		}
	}
	
	void Expect (int n) {
		if (la.kind==n) Get(); else { SynErr(n); }
	}
	
	boolean StartOf (int s) {
		return set[s][la.kind];
	}
	
	void ExpectWeak (int n, int follow) {
		if (la.kind == n) Get();
		else {
			SynErr(n);
			while (!StartOf(follow)) Get();
		}
	}
	
	boolean WeakSeparator (int n, int syFol, int repFol) {
		int kind = la.kind;
		if (kind == n) { Get(); return true; }
		else if (StartOf(repFol)) return false;
		else {
			SynErr(n);
			while (!(set[syFol][kind] || set[repFol][kind] || set[0][kind])) {
				Get();
				kind = la.kind;
			}
			return StartOf(syFol);
		}
	}
	
	void InlineAssembly() {
		LLVMI32Node node;
		Expect(4);
		node = AddSubOperation();
		Expect(4);
		root = factory.finishInline(node);
	}

	LLVMI32Node  AddSubOperation() {
		LLVMI32Node  n;
		String op; LLVMI32Node left = null, right = null;
		op = AddSubOp();
		if (StartOf(1)) {
			left = Register(1);
		} else if (la.kind == 27) {
			left = Immediate(1);
		} else SynErr(29);
		Expect(5);
		right = Register(2);
		Expect(6);
		n = factory.createBinary(op, left, right);
		return n;
	}

	String  AddSubOp() {
		String  op;
		op = la.val;
		switch (la.kind) {
		case 7: {
			Get();
			break;
		}
		case 8: {
			Get();
			break;
		}
		case 9: {
			Get();
			break;
		}
		case 10: {
			Get();
			break;
		}
		case 11: {
			Get();
			break;
		}
		case 12: {
			Get();
			break;
		}
		case 13: {
			Get();
			break;
		}
		case 14: {
			Get();
			break;
		}
		case 15: {
			Get();
			break;
		}
		case 16: {
			Get();
			break;
		}
		case 17: {
			Get();
			break;
		}
		case 18: {
			Get();
			break;
		}
		default: SynErr(30); break;
		}
		return op;
	}

	LLVMI32Node  Register(int index) {
		LLVMI32Node  n;
		switch (la.kind) {
		case 19: {
			Get();
			break;
		}
		case 20: {
			Get();
			break;
		}
		case 21: {
			Get();
			break;
		}
		case 22: {
			Get();
			break;
		}
		case 23: {
			Get();
			break;
		}
		case 24: {
			Get();
			break;
		}
		case 25: {
			Get();
			break;
		}
		case 26: {
			Get();
			break;
		}
		default: SynErr(31); break;
		}
		n = LLVMI32ArgNodeGen.create(index);
		return n;
	}

	LLVMI32Node  Immediate(int index) {
		LLVMI32Node  n;
		Expect(27);
		if (la.kind == 2) {
			Get();
		} else if (la.kind == 3) {
			Get();
		} else SynErr(32);
		n = LLVMI32ArgNodeGen.create(index);
		return n;
	}



	public LLVMInlineAssemblyRootNode Parse() {
		la = new Token();
		la.val = "";		
		Get();
		InlineAssembly();
		Expect(0);

		return root == null ? null : root;
	}

	private static final boolean[][] set = {
		{_T,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x},
		{_x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_x, _x,_x,_x,_T, _T,_T,_T,_T, _T,_T,_T,_x, _x,_x}

	};
	
} // end Parser


class Errors {
	public int count = 0;                                    // number of errors detected
	public java.io.PrintStream errorStream = System.out;     // error messages go to this stream
	public String errMsgFormat = "-- line {0} col {1}: {2}"; // 0=line, 1=column, 2=text
	
	protected void printMsg(int line, int column, String msg) {
		StringBuffer b = new StringBuffer(errMsgFormat);
		int pos = b.indexOf("{0}");
		if (pos >= 0) { b.delete(pos, pos+3); b.insert(pos, line); }
		pos = b.indexOf("{1}");
		if (pos >= 0) { b.delete(pos, pos+3); b.insert(pos, column); }
		pos = b.indexOf("{2}");
		if (pos >= 0) b.replace(pos, pos+3, msg);
		errorStream.println(b.toString());
	}
	
	public void SynErr (int line, int col, int n) {
		String s;
		switch (n) {
			case 0: s = "EOF expected"; break;
			case 1: s = "ident expected"; break;
			case 2: s = "number expected"; break;
			case 3: s = "hexNumber expected"; break;
			case 4: s = "\"\\\"\" expected"; break;
			case 5: s = "\",\" expected"; break;
			case 6: s = "\";\" expected"; break;
			case 7: s = "\"addb\" expected"; break;
			case 8: s = "\"adds\" expected"; break;
			case 9: s = "\"addw\" expected"; break;
			case 10: s = "\"addl\" expected"; break;
			case 11: s = "\"addq\" expected"; break;
			case 12: s = "\"addt\" expected"; break;
			case 13: s = "\"subb\" expected"; break;
			case 14: s = "\"subs\" expected"; break;
			case 15: s = "\"subw\" expected"; break;
			case 16: s = "\"subl\" expected"; break;
			case 17: s = "\"subq\" expected"; break;
			case 18: s = "\"subt\" expected"; break;
			case 19: s = "\"%eax\" expected"; break;
			case 20: s = "\"%ebx\" expected"; break;
			case 21: s = "\"%ecx\" expected"; break;
			case 22: s = "\"%edx\" expected"; break;
			case 23: s = "\"%esp\" expected"; break;
			case 24: s = "\"%ebp\" expected"; break;
			case 25: s = "\"%esi\" expected"; break;
			case 26: s = "\"%edi\" expected"; break;
			case 27: s = "\"$\" expected"; break;
			case 28: s = "??? expected"; break;
			case 29: s = "invalid AddSubOperation"; break;
			case 30: s = "invalid AddSubOp"; break;
			case 31: s = "invalid Register"; break;
			case 32: s = "invalid Immediate"; break;
			default: s = "error " + n; break;
		}
		printMsg(line, col, s);
		count++;
	}

	public void SemErr (int line, int col, String s) {	
		printMsg(line, col, s);
		count++;
	}
	
	public void SemErr (String s) {
		errorStream.println(s);
		count++;
	}
	
	public void Warning (int line, int col, String s) {	
		printMsg(line, col, s);
	}
	
	public void Warning (String s) {
		errorStream.println(s);
	}
	
} // Errors


class FatalError extends RuntimeException {
	public static final long serialVersionUID = 1L;
	public FatalError(String s) { super(s); }
}
