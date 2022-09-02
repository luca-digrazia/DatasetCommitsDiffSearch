/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Value;
import static org.junit.Assert.assertNotNull;
import com.oracle.truffle.tools.agentscript.AgentScript;
import java.io.ByteArrayOutputStream;
import java.util.function.Predicate;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.junit.Assert;

final class AgentObjectFactory extends ProxyLanguage {
    static TruffleObject agentObject;

    static AgentScriptAPI.OnConfig createConfig(boolean expressions, boolean statements, boolean roots, Predicate<String> rootNameFilter) {
        AgentScriptAPI.OnConfig config = new AgentScriptAPI.OnConfig();
        config.expressions = expressions;
        config.statements = statements;
        config.roots = roots;
        config.rootNameFilter = rootNameFilter;
        return config;
    }

    static Context newContext() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        return newContext(os, os);
    }

    static Context newContext(ByteArrayOutputStream out, ByteArrayOutputStream err) {
        return Context.newBuilder().out(out).err(err).allowExperimentalOptions(true).allowHostAccess(HostAccess.ALL).build();
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        final Source source = request.getSource();
        String scriptName = source.getName();
        return Truffle.getRuntime().createCallTarget(new AgentRootNode(ProxyLanguage.getCurrentLanguage(), scriptName, source));
    }

    public static Value createAgentObject(Context context) {
        cleanAgentObject();
        ProxyLanguage.setDelegate(new AgentObjectFactory());

        // BEGIN: AgentObjectFactory#createAgentObject
        final Engine engine = context.getEngine();
        Instrument instrument = engine.getInstruments().get(AgentScript.ID);
        AgentScript access = instrument.lookup(AgentScript.class);
        assertNotNull("Accessor found", access);
        Source agentSrc = createAgentSource();
        access.registerAgentScript(agentSrc);
        // END: AgentObjectFactory#createAgentObject

        Value value = context.eval(ProxyLanguage.ID, "");
        assertNotNull("Agent object has been initialized", agentObject);
        return value;
    }

    static void cleanAgentObject() {
        agentObject = null;
    }

    private static Source createAgentSource() {
        return Source.newBuilder(ProxyLanguage.ID, "", "agent.px").build();
    }

    private static class AgentRootNode extends RootNode {
        @Child private ValueNode node;
        private final String scriptName;
        private final Source source;

        AgentRootNode(TruffleLanguage<?> tl, String scriptName, Source source) {
            super(tl);
            this.scriptName = scriptName;
            this.source = source;
            this.node = new ValueNode();
        }

        @Override
        public Object execute(VirtualFrame frame) {
            if ("agent.px".equals(scriptName)) {
                Assert.assertNull("No agent object set yet", agentObject);
                agentObject = (TruffleObject) frame.getArguments()[0];
                return agentObject;
            } else {
                node.executeToFinishInitializationOfAgentObject(frame);
                return agentObject;
            }
        }

        @Override
        public SourceSection getSourceSection() {
            return source.createSection(0, source.getLength());
        }
    }

    @GenerateWrapper
    static class ValueNode extends Node implements InstrumentableNode {
        ValueNode() {
        }

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public SourceSection getSourceSection() {
            return getRootNode().getSourceSection();
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return StandardTags.RootBodyTag.class == tag || StandardTags.RootTag.class == tag;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new ValueNodeWrapper(this, probe);
        }

        @SuppressWarnings("unused")
        protected void executeToFinishInitializationOfAgentObject(VirtualFrame frame) {
        }

    }
}
