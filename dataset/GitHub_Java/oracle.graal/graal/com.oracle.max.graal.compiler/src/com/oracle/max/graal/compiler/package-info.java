/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
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

/**
 * The top-level package in C1X containing options, metrics, timers and the main compiler class
 * {@link com.oracle.max.graal.compiler.C1XCompiler}.
 *
 * <H2>{@code C1XCompiler} Overview</H2>
 *
 * C1X is intended to be used with multiple JVM's so makes no use of or reference to classes for a specific JVM, for
 * example Maxine.
 *
 * The compiler is represented by the class {@code C1XCompiler}. {@code C1XCompiler} binds a specific target
 * architecture and JVM interface to produce a usable compiler object. There are
 * two variants of {@code compileMethod}, one of which is used when doing <i>on stack replacement</i> (OSR), discussed
 * later. The main variant takes {@link com.sun.cri.ri.RiMethod} and {@link com.sun.cri.xir.RiXirGenerator} arguments.
 * {@code RiMethod} is C1X's representation of a Java method and {@code RiXirGenerator} represents the interface through
 * which the compiler requests the XIR for a given bytecode from the runtime system.
 *
 * <H3>The C1X Compilation Process</H3>
 *
 * {@link com.oracle.max.graal.compiler.C1XCompiler#compileMethod} creates a {@link C1XCompilation} instance and then returns the result of calling its
 * {@link com.oracle.max.graal.compiler.C1XCompilation#compile} method. The {@code C1XCompilation} instance records whether {@code compileMethod} was invoked with
 * the OSR variant, which is used later in the IR generation.
 * <p>
 * While there is only one {@code C1XCompiler} instance, there may be several compilations proceeding concurrently, each of
 * which is represented by a unique {@code C1XCompilation} instance. The static method {@link com.oracle.max.graal.compiler.C1XCompilation#current}} returns the
 * {@code C1XCompilation} instance associated with the current thread, and is managed using a {@link java.lang.ThreadLocal} variable. It
 * is used when assigning the unique id that is used for tracing  output to an HIR node. Each {@code C1XCompilation} instance
 * has an associated {@link com.sun.cri.ci.CiStatistics} object that accumulates information about the compilation process, but is also
 * used as a generator of, for example, basic block identifiers.
 * <p>
 * The compilation begins by calling {@link com.oracle.max.graal.compiler.C1XCompilation#emitHIR}, which creates the high-level intermediate representation (HIR) from the
 * bytecodes of the method. The HIR is managed by the {@link com.oracle.max.graal.compiler.graph.IR} class, an instance of which is created by
 * {@code emitHR}, which then calls the {{@link com.oracle.max.graal.compiler.graph.IR#build}} method and returns the result. The {@code C1XCompilation} and {@code IR}
 * instances are are bi-directionally linked.
 *
 * <H3>Supported backends</H3>
 *
 * <ul>
 * <li>AMD64/x64 with SSE2</li>
 * </ul>
 *
 * <H2>Notes and Todos</H2> This is a collection of notes about the C1X compiler, including future directions,
 * refactorings, missing features, broken features, etc.
 *
 *
 * <h3>Anticipated Refactorings</h3>
 *
 * <ul>
 * <li>
 * The HIR nodes {@link com.oracle.max.graal.compiler.ir.UnsafePrefetch}, {@link com.oracle.max.graal.compiler.ir.UnsafePutObject}, etc should be replaced by uses of the newer
 * {@link com.oracle.max.graal.compiler.ir.LoadPointer} and {@link com.oracle.max.graal.compiler.ir.StorePointer} nodes. Currently, the unsafe nodes are only generated by
 * the creation of an OSR entry. Benefit: reduce the number of different IR nodes.</li>
 *
 * <li>
 * Add a field to optionally store an {@link com.oracle.max.graal.compiler.ir.Info} object for each HIR node, and remove the
 * {@link com.oracle.max.graal.compiler.ir.Instruction#exceptionHandlers} field, the {@link com.oracle.max.graal.compiler.ir.Instruction#bci} field, and any fields to store the Java
 * frame state in subclasses. Benefit: saves space if most HIR nodes do not have exception handlers, a bci or Java frame
 * state. Removes virtual dispatch on accessing debug information for nodes. Allows any node, regardless of its type, to
 * have info attached.</li>
 *
 * <li>
 * Migrate all HIR nodes to use the immutable {@link com.oracle.max.graal.compiler.value.FrameStateInfo} for debugging information. The {@link com.oracle.max.graal.compiler.value.FrameState}
 * class is mutable and used throughout graph building. Benefit: {@code FrameStateInfo} would save both total space in
 * the IR graph prevent many bugs due to the mutability of {@code FrameState}.</li>
 *
 * <li>
 * Move the {@code FrameState} class to an inner class, or combine entirely, with the {@link com.oracle.max.graal.compiler.phases.GraphBuilderPhase} class. After
 * the introduction of the {@code FrameStateInfo} into HIR nodes, the mutable value stack should only need to be
 * accessed from the graph builder.</li>
 *
 * </ul>
 *
 * <h3>Missing or incomplete features</h3>
 *
 * There are some features of C1 that were not ported forward or finished given the time constraints for the C1X port. A
 * list appears below.
 *
 * <ul>
 * <li>
 * Deoptimization metadata. The locations of all local variables and stack values are not communicated back to the
 * runtime system through the {@link com.sun.cri.ci.CiDebugInfo} class yet. Such values are known to the register allocator, and there
 * vestigial logic to compute them still there in the
 * {@link com.oracle.max.graal.compiler.alloc.LinearScan#computeDebugInfo} method. To complete this metadata, the
 * {@link com.oracle.max.graal.compiler.alloc.LinearScan} class must implement the {@link ValueLocator} interface and pass it to the
 * {@link com.oracle.max.graal.compiler.lir.LIRDebugInfo#createFrame} method after register allocation. The
 * resulting debug info will be fed back to the runtime system by the existing logic that calls
 * {@link com.sun.cri.ci.CiTargetMethod#recordCall(int, Object, CiDebugInfo, boolean)} and other methods. Obviously the runtime
 * system will need to encode this metadata in a dense format, because it is huge.</li>
 *
 *
 * <li>
 * Tiered compilation support. C1 supported the ability to add instrumentation to branches, invocations, and checkcasts
 * in order to feed profile information to the C2 compiler in a tiered compilation setup. It relied on adding some
 * information to the HIR nodes that represent these operations ({@link Invoke}, {@link CheckCast}, etc). All of this
 * logic was removed to simplify both the front end and back end in anticipation of designing a future instrumentation
 * API. XIR should be general enough to allow instrumentation code to be added to invocation and checkcast sites, but
 * currently has no support for adding code at branches.
 *
 * </li>
 *
  * <li>
 * SPARC and other architecture support. There pretty well-delineated separation between the architecture-independent
 * part of LIR backend and the architecture-dependent, but the only implementation that current exists is the X86
 * backend ({@link com.oracle.max.graal.compiler.target.amd64.AMD64Backend}, {@link com.oracle.max.graal.compiler.target.amd64.AMD64LIRGenerator}, {@link com.oracle.max.graal.compiler.target.amd64.AMD64LIRAssembler}, etc).</li>
 *
 * <li>
 * XIR for safepoints. The C1X backend should use XIR to get the code for safepoints, but currently it still uses the
 * handwritten logic (currently only compatible with Maxine).</li>
 *
 * </ul>
 *
 * <h3>Untested features</h3>
 *
 * <ul>
 *
 * <li>
 * Reference map for outgoing overflow arguments. If a C1X method calls another method that has overflow arguments, it
 * is not clear if the outgoing overflow argument area, which may contain references, has the appropriate bits set in
 * the reference map for the C1X method's frame. Such arguments may be live in the called method.</li>
 *
 * <li>
 * Although it should work, inlining synchronized methods or methods with exception handlers hasn't been tested.</li>
 * <li>
 * On-stack replacement. C1X retains all of the special logic for performing an OSR compilation. This is basically a
 * compilation with a second entrypoint for entry from the interpreter. However, the generation of a runtime-specific
 * entry sequence was never tested.</li>
 *
 * <li>
 * {@link com.oracle.max.graal.compiler.C1XIntrinsic Intrinsification} is the mechanism by which the compiler recognizes calls to special JDK or
 * runtime methods and replaces them with custom code. It is enabled by the {@link com.oracle.max.graal.compiler.C1XOptions#OptIntrinsify} compiler
 * option. The C1X backend has never been tested with intrinsified arithmetic or floating point operations. For best
 * performance, it should generate specialized machine code for arithmetic and floating point, perhaps using global
 * stubs for complex floating point operations. <br>
 * <i>Note</i>: Folding of special intrinsified methods is supported, tested, and working. The runtime system may
 * register methods to be folded by using the
 * {@link com.oracle.max.graal.compiler.C1XIntrinsic#registerFoldableMethod(RiMethod, java.lang.reflect.Method)} call. When the compiler encounters a
 * call to such a registered method where the parameters are all constants, it invokes the supplied method with
 * reflection. If the reflective call produces a value and does not throw an exception, C1X replaces the call to the
 * method with the result.</li>
 * </ul>
 *
 * <h3>Broken features</h3>
 *
 * <ul>
 * <li>
 * {@link com.oracle.max.graal.compiler.opt.LoopPeeler Loop peeling} was written by Marcelo Cintra near the end of his internship. It was never completed
 * and should be considered broken. It only remains as a sketch of how loop peeling would be implemented in C1X, or in
 * case he would finish the implementation and test it.</li>
 *
 * <li>
 * Calls to global stubs should allocate space on the caller's stack. On AMD64 currently, calls to global stubs poke the
 * arguments onto the stack below the RSP (i.e. in the callee's stack). While normally this code sequence is
 * uninterruptible and works fine in the VM, signal handlers triggered when debugging or inspecting this code sequence
 * may destroy these values when the OS calls the signal handler. This requires knowing which global stubs are called
 * before finalizing the frame size; currently only the calls to
 * {@link com.oracle.max.graal.compiler.target.amd64.AMD64MacroAssembler#callRuntimeCalleeSaved}
 * do not fit this pattern. This needs to be fixed so that all global stubs that are called by the assembled code are
 * known before beginning assembling. The {@link com.oracle.max.graal.compiler.target.amd64.AMD64GlobalStubEmitter} controls how the global stubs accept their
 * parameters. See {@link com.oracle.max.graal.compiler.target.amd64.AMD64GlobalStubEmitter#callerFrameContainsArguments} and its usages.
 *
 * </li>
 * </ul>
 */
package com.oracle.max.graal.compiler;
