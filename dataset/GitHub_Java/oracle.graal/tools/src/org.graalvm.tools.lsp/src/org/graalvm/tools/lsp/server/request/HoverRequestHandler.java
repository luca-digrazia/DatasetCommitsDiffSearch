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
package org.graalvm.tools.lsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;

import org.graalvm.tools.lsp.instrument.LSPInstrument;
import org.graalvm.tools.lsp.server.ContextAwareExecutor;
import org.graalvm.tools.lsp.server.types.Hover;
import org.graalvm.tools.lsp.server.types.MarkupContent;
import org.graalvm.tools.lsp.server.types.MarkupKind;
import org.graalvm.tools.lsp.server.utils.CoverageData;
import org.graalvm.tools.lsp.server.utils.CoverageEventNode;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.ReadVariableTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.StandardTags.WriteVariableTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

@SuppressWarnings("deprecation")
public final class HoverRequestHandler extends AbstractRequestHandler {
    private static final TruffleLogger LOG = TruffleLogger.getLogger(LSPInstrument.ID, HoverRequestHandler.class);

    private final CompletionRequestHandler completionHandler;
    private final boolean developerMode;

    public HoverRequestHandler(Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor contextAwareExecutor, CompletionRequestHandler completionHandler, boolean developerMode) {
        super(env, surrogateMap, contextAwareExecutor);
        this.completionHandler = completionHandler;
        this.developerMode = developerMode;
    }

    public Hover hoverWithEnteredContext(URI uri, int line, int column) {
        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        InstrumentableNode nodeAtCaret = findNodeAtCaret(surrogate, line, column);
        if (nodeAtCaret != null) {
            SourceSection hoverSection = ((Node) nodeAtCaret).getSourceSection();
            LOG.log(Level.FINER, "Hover: SourceSection({0})", hoverSection.getCharacters());
            if (surrogate.hasCoverageData()) {
                List<CoverageData> coverages = surrogate.getCoverageData(hoverSection);
                if (coverages != null) {
                    return evalHoverInfos(coverages, hoverSection, surrogate.getLanguageInfo());
                }
            } else if (developerMode) {
                String sourceText = hoverSection.getCharacters().toString();
                MarkupContent content = MarkupContent.create(MarkupKind.PlainText,
                                "Language: " + surrogate.getLanguageId() + ", Section: " + sourceText + "\n" +
                                                "Node class: " + nodeAtCaret.getClass().getSimpleName() + "\n" +
                                                "Tags: " + getTags(nodeAtCaret));
                return Hover.create(content).setRange(SourceUtils.sourceSectionToRange(hoverSection));
            }
        }
        return Hover.create(Collections.emptyList());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String getTags(InstrumentableNode nodeAtCaret) {
        List<String> tags = new ArrayList<>();
        for (Class<Tag> tagClass : new Class[]{StatementTag.class, CallTag.class, RootTag.class, ExpressionTag.class, ReadVariableTag.class, WriteVariableTag.class}) {
            if (nodeAtCaret.hasTag(tagClass)) {
                tags.add(Tag.getIdentifier(tagClass));
            }
        }
        return tags.toString();
    }

    private Hover evalHoverInfos(List<CoverageData> coverages, SourceSection hoverSection, LanguageInfo langInfo) {
        String textAtHoverPosition = hoverSection.getCharacters().toString();
        for (CoverageData coverageData : coverages) {
            Hover frameSlotHover = tryFrameSlot(coverageData.getFrame(), textAtHoverPosition, langInfo, hoverSection);
            if (frameSlotHover != null) {
                return frameSlotHover;
            }

            Hover coverageDataHover = tryCoverageDataEvaluation(hoverSection, langInfo, textAtHoverPosition, coverageData);
            if (coverageDataHover != null) {
                return coverageDataHover;
            }
        }
        return Hover.create(Collections.emptyList());
    }

    private Hover tryCoverageDataEvaluation(SourceSection hoverSection, LanguageInfo langInfo, String textAtHoverPosition, CoverageData coverageData) {
        InstrumentableNode instrumentable = ((InstrumentableNode) coverageData.getCoverageEventNode().getInstrumentedNode());
        if (!instrumentable.hasTag(StandardTags.ExpressionTag.class)) {
            return null;
        }

        Future<Hover> future = contextAwareExecutor.executeWithNestedContext(() -> {
            final LanguageInfo rootLangInfo = coverageData.getCoverageEventNode().getRootNode().getLanguageInfo();
            final Source inlineEvalSource = Source.newBuilder(rootLangInfo.getId(), textAtHoverPosition, "in-line eval (hover request)").cached(false).build();
            ExecutableNode executableNode = null;
            try {
                executableNode = env.parseInline(inlineEvalSource, coverageData.getCoverageEventNode(), coverageData.getFrame());
            } catch (Exception e) {
                if (!(e instanceof TruffleException)) {
                    e.printStackTrace(err);
                }
            }
            if (executableNode == null) {
                return Hover.create(Collections.emptyList());
            }

            CoverageEventNode coverageEventNode = coverageData.getCoverageEventNode();
            coverageEventNode.insertOrReplaceChild(executableNode);
            Object evalResult = null;
            try {
                LOG.fine("Trying coverage-based eval...");
                evalResult = executableNode.execute(coverageData.getFrame());
            } catch (Exception e) {
                if (!((e instanceof TruffleException) || (e instanceof ControlFlowException))) {
                    e.printStackTrace(err);
                }
                return Hover.create(Collections.emptyList());
            } finally {
                coverageEventNode.clearChild();
            }

            if (evalResult instanceof TruffleObject) {
                Hover signatureHover = trySignature(hoverSection, langInfo, (TruffleObject) evalResult);
                if (signatureHover != null) {
                    return signatureHover;
                }
            }
            return Hover.create(createDefaultHoverInfos(textAtHoverPosition, evalResult, langInfo)).setRange(SourceUtils.sourceSectionToRange(hoverSection));
        }, true);

        return getFutureResultOrHandleExceptions(future);
    }

    private Hover trySignature(SourceSection hoverSection, LanguageInfo langInfo, TruffleObject evalResult) {
        String formattedSignature = completionHandler.getFormattedSignature(evalResult, langInfo);
        if (formattedSignature != null) {
            List<Object> contents = new ArrayList<>();
            contents.add(org.graalvm.tools.lsp.server.types.MarkedString.create(langInfo.getId(), formattedSignature));

            Object documentation = completionHandler.getDocumentation(evalResult, langInfo);
            if (documentation instanceof String) {
                contents.add(documentation);
            } else if (documentation instanceof MarkupContent) {
                MarkupContent markup = (MarkupContent) documentation;
                if (markup.getKind().equals(MarkupKind.PlainText)) {
                    contents.add(markup.getValue());
                } else {
                    contents.add(org.graalvm.tools.lsp.server.types.MarkedString.create(langInfo.getId(), markup.getValue()));
                }
            }
            return Hover.create(contents).setRange(SourceUtils.sourceSectionToRange(hoverSection));
        }

        return null;
    }

    private Hover tryFrameSlot(MaterializedFrame frame, String textAtHoverPosition, LanguageInfo langInfo, SourceSection hoverSection) {
        FrameSlot frameSlot = frame.getFrameDescriptor().getSlots().stream().filter(slot -> slot.getIdentifier().equals(textAtHoverPosition)).findFirst().orElseGet(() -> null);
        if (frameSlot != null) {
            Object frameSlotValue = frame.getValue(frameSlot);
            return Hover.create(createDefaultHoverInfos(textAtHoverPosition, frameSlotValue, langInfo)).setRange(SourceUtils.sourceSectionToRange(hoverSection));
        }
        return null;
    }

    private List<Object> createDefaultHoverInfos(String textAtHoverPosition, Object evalResultObject, LanguageInfo langInfo) {
        List<Object> contents = new ArrayList<>();
        contents.add(org.graalvm.tools.lsp.server.types.MarkedString.create(langInfo.getId(), textAtHoverPosition));
        String result = evalResultObject != null ? env.toString(langInfo, evalResultObject) : "";
        if (!textAtHoverPosition.equals(result)) {
            String resultObjectString = evalResultObject instanceof String ? "\"" + result + "\"" : result;
            contents.add(resultObjectString);
        }
        String detailText = completionHandler.createCompletionDetail(evalResultObject, langInfo);
        contents.add("meta-object: " + detailText);

        Object documentation = completionHandler.getDocumentation(evalResultObject, langInfo);
        if (documentation instanceof String) {
            contents.add(documentation);
        } else if (documentation instanceof MarkupContent) {
            MarkupContent markup = (MarkupContent) documentation;
            if (markup.getKind().equals(MarkupKind.PlainText)) {
                contents.add(markup.getValue());
            } else {
                contents.add(org.graalvm.tools.lsp.server.types.MarkedString.create(langInfo.getId(), markup.getValue()));
            }
        }
        return contents;
    }
}
