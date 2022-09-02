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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Value;
import static org.junit.Assert.assertNotNull;
import com.oracle.truffle.tools.agentscript.AgentScript;
import java.util.function.Predicate;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.junit.Assert;

final class AgentObjectFactory extends ProxyLanguage {
    private static TruffleObject agentObject;

    static AgentScriptAPI.OnConfig createConfig(boolean expressions, boolean statements, boolean roots, Predicate<String> rootNameFilter) {
        AgentScriptAPI.OnConfig config = new AgentScriptAPI.OnConfig();
        config.expressions = expressions;
        config.statements = statements;
        config.roots = roots;
        config.rootNameFilter = rootNameFilter;
        return config;
    }

    static Context newContext() {
        return Context.newBuilder().allowExperimentalOptions(true).allowHostAccess(HostAccess.ALL).build();
    }

    @Override
    protected CallTarget parse(ParsingRequest request) throws Exception {
        final Source source = request.getSource();
        String scriptName = source.getName();
        return Truffle.getRuntime().createCallTarget(new RootNode(ProxyLanguage.getCurrentLanguage()) {
            @Override
            public Object execute(VirtualFrame frame) {
                if ("agent.px".equals(scriptName)) {
                    Assert.assertNull("No agent object set yet", agentObject);
                    agentObject = (TruffleObject) frame.getArguments()[0];
                    return agentObject;
                } else {
                    assertNotNull("The agent object is created", agentObject);
                    return agentObject;
                }
            }

            @Override
            public SourceSection getSourceSection() {
                return source.createSection(0, source.getLength());
            }
        });
    }

    public static Value createAgentObject(Context context) {
        ProxyLanguage.setDelegate(new AgentObjectFactory());

        // BEGIN: AgentObjectFactory#createAgentObject
        final Engine engine = context.getEngine();
        Instrument instrument = engine.getInstruments().get(AgentScript.ID);
        AgentScript access = instrument.lookup(AgentScript.class);
        assertNotNull("Accessor found", access);
        Source agentSrc = createAgentSource();
        access.registerAgentScript(agentSrc);
        // END: AgentObjectFactory#createAgentObject

        agentObject = null;
        Value value = context.eval(ProxyLanguage.ID, "");
        assertNotNull("Agent object has been initialized", agentObject);
        return value;
    }

    private static Source createAgentSource() {
        return Source.newBuilder(ProxyLanguage.ID, "", "agent.px").build();
    }
}
