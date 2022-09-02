/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.espresso.jdwp.api.LineBreakpointInfo;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.jdwp.api.VMEventListeners;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class RequestedJDWPEvents {

    public static final byte SINGLE_STEP = 1;
    public static final byte BREAKPOINT = 2;
    public static final byte FRAME_POP = 3;
    public static final byte EXCEPTION = 4;
    public static final byte USER_DEFINED = 5;
    public static final byte THREAD_START = 6;
    public static final byte THREAD_DEATH = 7;
    public static final byte CLASS_PREPARE = 8;
    public static final byte CLASS_UNLOAD = 9;
    public static final byte CLASS_LOAD = 10;
    public static final byte FIELD_ACCESS = 20;
    public static final byte FIELD_MODIFICATION = 21;
    public static final byte EXCEPTION_CATCH = 30;
    public static final byte METHOD_ENTRY = 40;
    public static final byte METHOD_EXIT = 41;
    public static final byte METHOD_EXIT_WITH_RETURN_VALUE = 42;
    public static final byte MONITOR_CONTENDED_ENTER = 43;
    public static final byte MONITOR_CONTENDED_ENTERED = 44;
    public static final byte MONITOR_WAIT = 45;
    public static final byte MONITOR_WAITED = 46;
    public static final byte VM_START = 90;
    public static final byte VM_DEATH = 99;
    public static final byte VM_DISCONNECTED = 100;

    private final VMEventListener eventListener;
    private final Ids ids;

    RequestedJDWPEvents(JDWPContext context, SocketConnection connection) {
        this.eventListener = new VMEventListenerImpl(connection, context);
        VMEventListeners.getDefault().registerListener(eventListener);
        this.ids = context.getIds();
    }

    public JDWPResult registerEvent(Packet packet, JDWPCommands callback, JDWPContext context) {
        PacketStream reply = null;
        ArrayList<Callable<Void>> futures = new ArrayList<>();
        PacketStream input = new PacketStream(packet);

        byte eventKind = input.readByte();
        byte suspendPolicy = input.readByte();
        int modifiers = input.readInt();

        RequestFilter filter = new RequestFilter(packet.id, eventKind, modifiers);
        for (int i = 0; i < modifiers; i++) {
            byte modKind = input.readByte();
            handleModKind(filter, input, modKind, callback, context);
        }

        switch (eventKind) {
            case SINGLE_STEP:
            case METHOD_EXIT_WITH_RETURN_VALUE:
                reply = toReply(packet);
                break;
            case BREAKPOINT:
                eventListener.addBreakpointRequest(filter.getRequestId(), filter.getBreakpointInfo());
                futures.add(callback.createLineBreakpointCommand(suspendPolicy, filter.getBreakpointInfo()));
                reply = toReply(packet);
                break;
            case EXCEPTION:
                eventListener.addBreakpointRequest(filter.getRequestId(), filter.getBreakpointInfo());
                futures.add(callback.createExceptionBreakpoint(filter.getBreakpointInfo()));
                reply = toReply(packet);
                break;
            case CLASS_PREPARE:
                Callable<Void> callable = eventListener.addClassPrepareRequest(new ClassPrepareRequest(filter));
                if (callable != null) {
                    futures.add(callable);
                }
                reply = toReply(packet);
                break;
            case THREAD_START:
                eventListener.addThreadStartedRequestId(packet.id);
                reply = toReply(packet);
                break;
            case THREAD_DEATH:
                eventListener.addThreadDiedRequestId(packet.id);
                reply = toReply(packet);
                break;
            case CLASS_UNLOAD:
                eventListener.addClassUnloadRequestId(packet.id);
                reply = toReply(packet);
                break;
            default:
                System.out.println("unhandled event kind " + eventKind);
                break;
        }

        // register the request filter for this event
        EventFilters.getDefault().addFilter(filter);
        return new JDWPResult(reply, futures);
    }

    private PacketStream toReply(Packet packet) {
        PacketStream reply;
        reply = new PacketStream().replyPacket().id(packet.id);
        reply.writeInt(packet.id);
        return reply;
    }

    private void handleModKind(RequestFilter filter, PacketStream input, byte modKind, JDWPCommands callback, JDWPContext context) {
        switch (modKind) {
            case 1:
                int count = input.readInt();
                filter.addEventCount(count);
                break;
            case 2:
                System.err.println("unhandled modKind 2");
                break;
            case 3: // limit to specific thread
                long threadId = input.readLong();
                Object thread = context.getIds().fromId((int) threadId);
                filter.addThread(thread);
                break;
            case 4:
                long refTypeId = input.readLong();
                filter.addRefTypeLimit((KlassRef) ids.fromId((int) refTypeId));
                break;
            case 5: // class positive pattern
                String classPattern = input.readString();
                try {
                    Pattern pattern = Pattern.compile(classPattern);
                    filter.addPositivePattern(pattern);
                } catch (PatternSyntaxException ex) {
                    // wrong input pattern, silently ignore this breakpoint request then
                }
                break;
            case 6:
                classPattern = input.readString();
                try {
                    Pattern pattern = Pattern.compile(classPattern);
                    filter.addExcludePattern(pattern);
                } catch (PatternSyntaxException ex) {
                    // wrong input pattern, silently ignore this breakpoint request then
                }
                break;
            case 7: // location-specific
                byte typeTag = input.readByte();
                long classId = input.readLong();
                long methodId = input.readLong();
                long bci = input.readLong();

                KlassRef klass = (KlassRef) ids.fromId((int) classId);
                String slashName = klass.getTypeAsString();
                MethodRef method = (MethodRef) ids.fromId((int) methodId);
                int line = method.BCItoLineNumber((int) bci);

                LineBreakpointInfo info = new LineBreakpointInfo(filter, typeTag, classId, methodId, bci, slashName, line);
                filter.addBreakpointInfo(info);
                break;
            case 8:
                refTypeId = input.readLong();
                klass = null;
                if (refTypeId != 0) {
                    klass = (KlassRef) context.getIds().fromId((int) refTypeId);
                }

                boolean caught = input.readBoolean();
                boolean unCaught = input.readBoolean();
                ExceptionBreakpointInfo exceptionBreakpointInfo = new ExceptionBreakpointInfo(filter, klass, caught, unCaught);
                filter.addBreakpointInfo(exceptionBreakpointInfo);
                break;
            case 9:
                System.err.println("unhandled modKind 9");
                break;
            case 10:
                filter.setStepping(true);
                threadId = input.readLong();
                thread = context.getIds().fromId((int) threadId);

                /*int size =*/ input.readInt();

                int depth = input.readInt();
                switch (depth) {
                    case SteppingConstants.INTO:
                        callback.stepInto(thread, filter.getRequestId());
                        break;
                    case SteppingConstants.OVER:
                        callback.stepOver(thread, filter.getRequestId());
                        break;
                    case SteppingConstants.OUT:
                        callback.stepOut(thread, filter.getRequestId());
                        break;
                }
                break;
            case 11:
                System.err.println("unhandled modKind 11");
                break;
            case 12:
                System.err.println("unhandled modKind 12");
                break;
            default:
                break;
        }
    }

    public JDWPResult clearRequest(Packet packet) {
        PacketStream reply = new PacketStream().id(packet.id).replyPacket();
        PacketStream input = new PacketStream(packet);

        byte eventKind = input.readByte();
        int requestId = input.readInt();
        RequestFilter requestFilter = EventFilters.getDefault().getRequestFilter(requestId);

        if (requestFilter != null) {
            byte kind = requestFilter.getEventKind();
            if (kind == eventKind) {
                switch (eventKind) {
                    case SINGLE_STEP:
                        //System.out.println("clear single step not implemented");
                        break;
                    case METHOD_EXIT_WITH_RETURN_VALUE:
                        break;
                    case BREAKPOINT:
                    case EXCEPTION:
                        eventListener.removeBreakpointRequest(requestFilter.getRequestId());
                        break;
                    case CLASS_PREPARE:
                        eventListener.removeClassPrepareRequest(requestFilter.getRequestId());
                        break;
                    case THREAD_START:
                        eventListener.addThreadStartedRequestId(packet.id);
                        break;
                    case THREAD_DEATH:
                        eventListener.addThreadDiedRequestId(packet.id);
                        break;
                    case CLASS_UNLOAD:
                        eventListener.addClassUnloadRequestId(packet.id);
                        break;
                    default:
                        System.out.println("unhandled event clear kind " + eventKind);
                        break;
                }
            } else {
                reply.errorCode(JDWPErrorCodes.INVALID_EVENT_TYPE);
            }
        } else {
            reply.errorCode(JDWPErrorCodes.INVALID_EVENT_TYPE);
        }

        return new JDWPResult(reply);
    }
}
