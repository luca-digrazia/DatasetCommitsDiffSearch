/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrument;

/**
 * A <em>binding</em> between:
 * <ol>
 * <li>A {@link SyntaxTag} that specifies nodes to act as source of <em>execution events</em> taking
 * place at a program location in an executing Truffle AST, and</li>
 * <li>A <em>listener</em>: a consumer of execution events on behalf of an external client.
 * </ol>
 * <p>
 * Client-oriented documentation for the use of Instruments is available online at <a
 * HREF="https://wiki.openjdk.java.net/display/Graal/Listening+for+Execution+Events" >https://
 * wiki.openjdk.java.net/display/Graal/Listening+for+Execution+Events</a>
 *
 * @see SyntaxTag
 * @see Instrumenter
 */
public abstract class TagInstrument extends Instrument {

    /**
     * Optional documentation, mainly for debugging.
     */
    @SuppressWarnings("unused") private final String instrumentInfo;

    protected Instrumenter instrumenter;

    /**
     * Has this instrument been disposed? stays true once set.
     */
    protected boolean isDisposed = false;

    private SyntaxTag tag = null;

    protected TagInstrument(Instrumenter instrumenter, SyntaxTag tag, String instrumentInfo) {
        this.instrumenter = instrumenter;
        this.tag = tag;
        this.instrumentInfo = instrumentInfo;
    }

    @Override
    public void dispose() throws IllegalStateException {
        if (isDisposed) {
            throw new IllegalStateException("Attempt to dispose an already disposed Instrumennt");
        }
        instrumenter.disposeAfterTagInstrument();
        this.isDisposed = true;
    }

    @Override
    public boolean isDisposed() {
        return isDisposed;
    }

    public SyntaxTag getTag() {
        return tag;
    }

    static final class BeforeTagInstrument extends TagInstrument {

        private final StandardBeforeInstrumentListener listener;

        BeforeTagInstrument(Instrumenter instrumenter, SyntaxTag tag, StandardBeforeInstrumentListener listener, String instrumentInfo) {
            super(instrumenter, tag, instrumentInfo);
            this.listener = listener;
        }

        StandardBeforeInstrumentListener getListener() {
            return listener;
        }

        @Override
        public void dispose() throws IllegalStateException {
            if (isDisposed) {
                throw new IllegalStateException("Disposed Instrument can not be disposed again");
            }
            instrumenter.disposeBeforeTagInstrument();
            this.isDisposed = true;
        }
    }

    static final class AfterTagInstrument extends TagInstrument {

        private final StandardAfterInstrumentListener listener;

        AfterTagInstrument(Instrumenter instrumenter, SyntaxTag tag, StandardAfterInstrumentListener listener, String instrumentInfo) {
            super(instrumenter, tag, instrumentInfo);
            this.listener = listener;
        }

        StandardAfterInstrumentListener getListener() {
            return listener;
        }

        @Override
        public void dispose() throws IllegalStateException {
            if (isDisposed) {
                throw new IllegalStateException("Disposed Instrument can not be disposed again");
            }
            instrumenter.disposeAfterTagInstrument();
            this.isDisposed = true;
        }
    }
}
