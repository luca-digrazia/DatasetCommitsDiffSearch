/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler.test;

import java.io.ByteArrayOutputStream;

import org.junit.Before;

import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

public abstract class AbstractProfilerTest {

    protected PolyglotEngine engine;
    protected final ByteArrayOutputStream out = new ByteArrayOutputStream();
    protected final ByteArrayOutputStream err = new ByteArrayOutputStream();

    // @formatter:off
    protected final Source defaultSource = makeSource(
            "ROOT(" + 
                    "DEFINE(foo,ROOT(STATEMENT))," +
                    "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," +
                    "DEFINE(baz,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar)))))," +
                    "CALL(baz),CALL(bar)" +
            ")");

    protected final Source defaultRecursiveSource = makeSource(
            "ROOT(" +
                    "DEFINE(foo,ROOT(BLOCK(STATEMENT,RECURSIVE_CALL(foo))))," +
                    "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," +
                    "CALL(bar)" +
            ")");

    protected Source makeSource(String s) {
        return Source.newBuilder(s).mimeType(InstrumentationTestLanguage.MIME_TYPE).name("test").build();
    }

	protected static final SourceSectionFilter NO_INTERNAL_ROOT_TAG_FILTER = SourceSectionFilter
			.newBuilder().sourceIs(s -> !s.isInternal())
			// .rootNameIs(name -> !"create".equals(name))
			.tagIs(StandardTags.RootTag.class).build();
	protected static final SourceSectionFilter NO_INTERNAL_CALL_TAG_FILTER = SourceSectionFilter
			.newBuilder().sourceIs(s -> !s.isInternal())
			.rootNameIs(name -> !"create".equals(name))
			.tagIs(StandardTags.CallTag.class).build();
	protected static final SourceSectionFilter NO_INTERNAL_STATEMENT_TAG_FILTER = SourceSectionFilter
			.newBuilder().sourceIs(s -> !s.isInternal())
			.rootNameIs(name -> !"create".equals(name))
			.tagIs(StandardTags.StatementTag.class).build();

    // @formatter:on

    @Before
    public void setup() {
        engine = PolyglotEngine.newBuilder().setIn(System.in).setOut(out).setErr(err).build();
    }

    protected void execute(Source source) {
        engine.eval(source).get();
    }

}
