/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler;

import com.oracle.max.graal.compiler.debug.TTY.*;

/**
 * This class encapsulates options that control the behavior of the C1X compiler.
 * The help message for each option is specified by a {@linkplain #helpMap help map}.
 *
 * (tw) WARNING: Fields of this class are treated as final by Graal.
 *
 * @author Ben L. Titzer
 */
public final class C1XOptions {

    // Checkstyle: stop
    private static final boolean ____ = false;
    // Checkstyle: resume

    // inlining settings
    public static boolean Inline                             = ____;
    public static int     MaximumInstructionCount            = 37000;
    public static float   MaximumInlineRatio                 = 0.90f;
    public static int     MaximumInlineSize                  = 35;
    public static int     MaximumTrivialSize                 = 6;
    public static int     MaximumInlineLevel                 = 9;
    public static int     MaximumRecursiveInlineLevel        = 2;
    public static int     MaximumDesiredSize                 = 8000;
    public static int     MaximumShortLoopSize               = 5;

    // debugging settings
    public static boolean VerifyPointerMaps                  = ____;
    public static int     MethodEndBreakpointGuards          = 0;
    public static boolean ZapStackOnMethodEntry              = ____;
    public static boolean StressLinearScan                   = ____;
    public static boolean BailoutOnException                 = ____;

    /**
     * See {@link Filter#Filter(String, Object)}.
     */
    public static String  PrintFilter                        = null;

    // printing settings
    public static boolean PrintLIR                           = ____;
    public static boolean PrintCFGToFile                     = ____;

    // DOT output settings
    public static boolean PrintDOTGraphToFile                = ____;
    public static boolean PrintDOTGraphToPdf                 = ____;
    public static boolean OmitDOTFrameStates                 = ____;

    // Ideal graph visualizer output settings
    public static int     PrintIdealGraphLevel               = 0;
    public static boolean PrintIdealGraphFile                = ____;
    public static String  PrintIdealGraphAddress             = "127.0.0.1";
    public static int     PrintIdealGraphPort                = 4444;

    // Other printing settings
    public static boolean PrintMetrics                       = ____;
    public static boolean PrintTimers                        = ____;
    public static boolean PrintCompilation                   = ____;
    public static boolean PrintXirTemplates                  = ____;
    public static boolean PrintIRWithLIR                     = ____;
    public static boolean PrintAssembly                      = ____;
    public static boolean PrintCodeBytes                     = ____;
    public static int     PrintAssemblyBytesPerLine          = 16;
    public static int     TraceLinearScanLevel               = 0;
    public static int     TraceLIRGeneratorLevel             = 0;
    public static boolean TraceRelocation                    = ____;
    public static boolean TraceLIRVisit                      = ____;
    public static boolean TraceAssembler                     = ____;
    public static boolean TraceInlining                      = ____;
    public static boolean TraceDeadCodeElimination           = ____;
    public static int     TraceBytecodeParserLevel           = 0;
    public static boolean QuietBailout                       = ____;

    // state merging settings
    public static boolean AssumeVerifiedBytecode             = ____;

    // Linear scan settings
    public static boolean CopyPointerStackArguments          = true;

    // Code generator settings
    public static boolean GenLIR                             = true;
    public static boolean GenCode                            = true;

    public static boolean UseConstDirectCall                 = false;

    public static boolean GenSpecialDivChecks                = ____;
    public static boolean GenAssertionCode                   = ____;
    public static boolean AlignCallsForPatching              = true;
    public static boolean NullCheckUniquePc                  = ____;
    public static boolean InvokeSnippetAfterArguments        = ____;
    public static boolean ResolveClassBeforeStaticInvoke     = true;

    // Translating tableswitch instructions
    public static int     SequentialSwitchLimit              = 4;
    public static int     RangeTestsSwitchDensity            = 5;

    public static boolean DetailedAsserts                    = ____;

    // Runtime settings
    public static int     ReadPrefetchInstr                  = 0;
    public static int     StackShadowPages                   = 2;

    // Assembler settings
    public static boolean CommentedAssembly                  = ____;
    public static boolean PrintLIRWithAssembly               = ____;

    public static boolean OptCanonicalizer                   = true;
}
