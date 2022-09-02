/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.truffle.regex.literal;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.regex.CompiledRegex;
import com.oracle.truffle.regex.RegexExecRootNode;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexObject;
import com.oracle.truffle.regex.RegexRootNode;
import com.oracle.truffle.regex.RegexSource;
import com.oracle.truffle.regex.result.PreCalculatedResultFactory;
import com.oracle.truffle.regex.result.RegexResult;
import com.oracle.truffle.regex.tregex.nodes.input.InputAccess;
import com.oracle.truffle.regex.tregex.parser.ast.visitors.PreCalcResultVisitor;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public abstract class LiteralRegexExecRootNode extends RegexExecRootNode implements CompiledRegex, JsonConvertible {

    protected final String literal;
    protected final PreCalculatedResultFactory resultFactory;
    private final CallTarget regexCallTarget;

    public LiteralRegexExecRootNode(RegexLanguage language, RegexSource source, PreCalcResultVisitor preCalcResultVisitor) {
        super(language, source);
        this.literal = preCalcResultVisitor.getLiteral();
        this.resultFactory = preCalcResultVisitor.getResultFactory();
        regexCallTarget = Truffle.getRuntime().createCallTarget(new RegexRootNode(language, this));
    }

    @Override
    protected final String getEngineLabel() {
        return "literal";
    }

    @Override
    public CallTarget getRegexCallTarget() {
        return regexCallTarget;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("method", getImplName()),
                        Json.prop("literal", DebugUtil.escapeString(literal)),
                        Json.prop("factory", resultFactory));
    }

    protected abstract String getImplName();

    public static final class EmptyIndexOf extends LiteralRegexExecRootNode {

        public EmptyIndexOf(RegexLanguage language, RegexSource source, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, source, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "emptyIndexOf";
        }

        @Override
        protected RegexResult execute(VirtualFrame frame, RegexObject regex, Object input, int fromIndex) {
            return resultFactory.createFromStart(regex, input, fromIndex);
        }
    }

    public static final class EmptyStartsWith extends LiteralRegexExecRootNode {

        public EmptyStartsWith(RegexLanguage language, RegexSource source, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, source, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "emptyStartsWith";
        }

        @Override
        protected RegexResult execute(VirtualFrame frame, RegexObject regex, Object input, int fromIndex) {
            return fromIndex == 0 ? resultFactory.createFromStart(regex, input, 0) : RegexResult.NO_MATCH;
        }
    }

    public static final class EmptyEndsWith extends LiteralRegexExecRootNode {

        public EmptyEndsWith(RegexLanguage language, RegexSource source, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, source, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "emptyEndsWith";
        }

        @Override
        protected RegexResult execute(VirtualFrame frame, RegexObject regex, Object input, int fromIndex) {
            assert fromIndex <= InputAccess.length(input);
            return resultFactory.createFromEnd(regex, input, InputAccess.length(input));
        }
    }

    public static final class EmptyEquals extends LiteralRegexExecRootNode {

        public EmptyEquals(RegexLanguage language, RegexSource source, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, source, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "emptyEquals";
        }

        @Override
        protected RegexResult execute(VirtualFrame frame, RegexObject regex, Object input, int fromIndex) {
            assert fromIndex <= InputAccess.length(input);
            return InputAccess.length(input) == 0 ? resultFactory.createFromStart(regex, input, 0) : RegexResult.NO_MATCH;
        }
    }

    public static final class IndexOfChar extends LiteralRegexExecRootNode {

        @CompilationFinal(dimensions = 1) private final char[] c;

        public IndexOfChar(RegexLanguage language, RegexSource source, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, source, preCalcResultVisitor);
            assert literal.length() == 1;
            c = new char[]{literal.charAt(0)};
        }

        @Override
        protected String getImplName() {
            return "indexOfChar";
        }

        @Override
        protected RegexResult execute(VirtualFrame frame, RegexObject regex, Object input, int fromIndex) {
            int start = InputAccess.indexOf(input, fromIndex, InputAccess.length(input), c);
            if (start == -1) {
                return RegexResult.NO_MATCH;
            }
            return resultFactory.createFromStart(regex, input, start);
        }
    }

    public static final class IndexOfString extends LiteralRegexExecRootNode {

        public IndexOfString(RegexLanguage language, RegexSource source, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, source, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "indexOfString";
        }

        @Override
        protected RegexResult execute(VirtualFrame frame, RegexObject regex, Object input, int fromIndex) {
            int start = InputAccess.indexOfString(input, literal, fromIndex, InputAccess.length(input));
            if (start == -1) {
                return RegexResult.NO_MATCH;
            }
            return resultFactory.createFromStart(regex, input, start);
        }
    }

    public static final class StartsWith extends LiteralRegexExecRootNode {

        public StartsWith(RegexLanguage language, RegexSource source, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, source, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "startsWith";
        }

        @Override
        protected RegexResult execute(VirtualFrame frame, RegexObject regex, Object input, int fromIndex) {
            return fromIndex == 0 && InputAccess.startsWith(input, literal) ? resultFactory.createFromStart(regex, input, 0) : RegexResult.NO_MATCH;
        }
    }

    public static final class EndsWith extends LiteralRegexExecRootNode {

        public EndsWith(RegexLanguage language, RegexSource source, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, source, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "endsWith";
        }

        @Override
        protected RegexResult execute(VirtualFrame frame, RegexObject regex, Object input, int fromIndex) {
            return fromIndex <= InputAccess.length(input) - literal.length() && InputAccess.endsWith(input, literal) ? resultFactory.createFromEnd(regex, input, InputAccess.length(input))
                            : RegexResult.NO_MATCH;
        }
    }

    public static final class Equals extends LiteralRegexExecRootNode {

        public Equals(RegexLanguage language, RegexSource source, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, source, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "equals";
        }

        @Override
        protected RegexResult execute(VirtualFrame frame, RegexObject regex, Object input, int fromIndex) {
            return fromIndex == 0 && InputAccess.equals(input, literal) ? resultFactory.createFromStart(regex, input, 0) : RegexResult.NO_MATCH;
        }
    }

    public static final class RegionMatches extends LiteralRegexExecRootNode {

        public RegionMatches(RegexLanguage language, RegexSource source, PreCalcResultVisitor preCalcResultVisitor) {
            super(language, source, preCalcResultVisitor);
        }

        @Override
        protected String getImplName() {
            return "regionMatches";
        }

        @Override
        protected RegexResult execute(VirtualFrame frame, RegexObject regex, Object input, int fromIndex) {
            return InputAccess.regionMatches(input, literal, fromIndex) ? resultFactory.createFromStart(regex, input, fromIndex) : RegexResult.NO_MATCH;
        }
    }
}
