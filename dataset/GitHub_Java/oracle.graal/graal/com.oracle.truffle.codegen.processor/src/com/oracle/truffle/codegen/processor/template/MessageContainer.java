/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.codegen.processor.template;

import java.util.*;

import javax.lang.model.element.*;
import javax.tools.Diagnostic.Kind;

import com.oracle.truffle.codegen.processor.*;

public abstract class MessageContainer {

    private final List<Message> messages = new ArrayList<>();

    public final void addWarning(String text, Object... params) {
        getMessages().add(new Message(this, String.format(text, params), Kind.WARNING));
    }

    public final void addError(String text, Object... params) {
        getMessages().add(new Message(this, String.format(text, params), Kind.ERROR));
    }

    protected List<MessageContainer> findChildContainers() {
        return Collections.emptyList();
    }

    public abstract Element getMessageElement();

    public final void emitMessages(TypeElement baseElement, Log log) {
        emitMessagesImpl(baseElement, log, new HashSet<MessageContainer>());
    }

    private void emitMessagesImpl(TypeElement baseElement, Log log, Set<MessageContainer> visitedSinks) {
        for (Message message : getMessages()) {
            emitDefault(baseElement, log, message);
        }

        for (MessageContainer sink : findChildContainers()) {
            if (visitedSinks.contains(sink)) {
                return;
            }

            visitedSinks.add(sink);
            sink.emitMessagesImpl(baseElement, log, visitedSinks);
        }
    }

    private void emitDefault(TypeElement baseType, Log log, Message message) {
        if (Utils.typeEquals(baseType.asType(), Utils.findRootEnclosingType(getMessageElement()).asType()) && this == message.getOriginalContainer()) {
            log.message(message.getKind(), getMessageElement(), getMessageAnnotation(), getMessageAnnotationValue(), message.getText());
        } else {
            MessageContainer original = message.getOriginalContainer();
            log.message(message.getKind(), baseType, null, null, wrapText(original.getMessageElement(), original.getMessageAnnotation(), message.getText()));
        }
    }

    private static String wrapText(Element element, AnnotationMirror mirror, String text) {
        StringBuilder b = new StringBuilder();
        if (element != null) {
            b.append("Element " + element.toString());
        }
        if (mirror != null) {
            b.append(" at annotation @" + Utils.getSimpleName(mirror.getAnnotationType()));
        }

        if (b.length() > 0) {
            b.append(" is erroneous: ").append(text);
            return b.toString();
        } else {
            return text;
        }
    }

    public AnnotationMirror getMessageAnnotation() {
        return null;
    }

    public AnnotationValue getMessageAnnotationValue() {
        return null;
    }

    public final boolean hasErrors() {
        return hasErrorsImpl(new HashSet<MessageContainer>());
    }

    public final List<Message> collectMessages() {
        List<Message> collectedMessages = new ArrayList<>();
        collectMessagesImpl(collectedMessages, new HashSet<MessageContainer>());
        return collectedMessages;
    }

    private void collectMessagesImpl(List<Message> collectedMessages, Set<MessageContainer> visitedSinks) {
        collectedMessages.addAll(getMessages());
        for (MessageContainer sink : findChildContainers()) {
            if (visitedSinks.contains(sink)) {
                return;
            }

            visitedSinks.add(sink);
            sink.collectMessagesImpl(collectedMessages, visitedSinks);
        }
    }

    private boolean hasErrorsImpl(Set<MessageContainer> visitedSinks) {
        for (Message msg : getMessages()) {
            if (msg.getKind() == Kind.ERROR) {
                return true;
            }
        }
        for (MessageContainer sink : findChildContainers()) {
            if (visitedSinks.contains(sink)) {
                return false;
            }

            visitedSinks.add(sink);

            if (sink.hasErrorsImpl(visitedSinks)) {
                return true;
            }
        }
        return false;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public static class Message {

        private final MessageContainer originalContainer;
        private final String text;
        private final Kind kind;

        public Message(MessageContainer originalContainer, String text, Kind kind) {
            this.originalContainer = originalContainer;
            this.text = text;
            this.kind = kind;
        }

        public MessageContainer getOriginalContainer() {
            return originalContainer;
        }

        public String getText() {
            return text;
        }

        public Kind getKind() {
            return kind;
        }

        @Override
        public String toString() {
            return kind + ": " + text;
        }

    }

}
