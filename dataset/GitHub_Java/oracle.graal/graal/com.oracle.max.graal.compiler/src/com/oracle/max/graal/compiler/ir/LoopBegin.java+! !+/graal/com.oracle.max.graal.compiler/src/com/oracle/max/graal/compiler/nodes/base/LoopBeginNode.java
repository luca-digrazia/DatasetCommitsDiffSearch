/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.ir;

import java.util.*;

import com.oracle.max.graal.compiler.nodes.base.*;
import com.oracle.max.graal.compiler.nodes.spi.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

public class LoopBegin extends Merge {

    private double loopFrequency;

    public LoopBegin(Graph graph) {
        super(graph);
        loopFrequency = 1;
    }

    public double loopFrequency() {
        return loopFrequency;
    }

    public void setLoopFrequency(double loopFrequency) {
        this.loopFrequency = loopFrequency;
    }

    public LoopEnd loopEnd() {
        for (Node usage : usages()) {
            if (usage instanceof LoopEnd) {
                LoopEnd end = (LoopEnd) usage;
                if (end.loopBegin() == this) {
                    return end;
                }
            }
        }
        return null;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitLoopBegin(this);
    }

    @Override
    public int phiPredecessorCount() {
        return 2;
    }

    @Override
    public int phiPredecessorIndex(Node pred) {
        if (pred == forwardEdge()) {
            return 0;
        } else if (pred == this.loopEnd()) {
            return 1;
        }
        throw Util.shouldNotReachHere("unknown pred : " + pred + "(sp=" + forwardEdge() + ", le=" + this.loopEnd() + ")");
    }

    @Override
    public Node phiPredecessorAt(int index) {
        if (index == 0) {
            return forwardEdge();
        } else if (index == 1) {
            return loopEnd();
        }
        throw Util.shouldNotReachHere();
    }

    public Collection<InductionVariable> inductionVariables() {
        return Util.filter(this.usages(), InductionVariable.class);
    }

    @Override
    public Iterable<? extends Node> phiPredecessors() {
        return Arrays.asList(new Node[]{this.forwardEdge(), this.loopEnd()});
    }

    public EndNode forwardEdge() {
        return this.endAt(0);
    }

    public LoopCounter loopCounter() {
        return loopCounter(CiKind.Long);
    }

    public LoopCounter loopCounter(CiKind kind) {
        for (Node usage : usages()) {
            if (usage instanceof LoopCounter && ((LoopCounter) usage).kind == kind) {
                return (LoopCounter) usage;
            }
        }
        return new LoopCounter(kind, this, graph());
    }

    @Override
    public boolean verify() {
        assertTrue(loopEnd() != null);
        assertTrue(forwardEdge() != null);
        return true;
    }

    @Override
    public String toString() {
        return "LoopBegin: " + super.toString();
    }

    @Override
    public Iterable< ? extends Node> dataUsages() {
        final Iterator< ? extends Node> dataUsages = super.dataUsages().iterator();
        return new Iterable<Node>() {
            @Override
            public Iterator<Node> iterator() {
                return new StateSplit.FilteringIterator(dataUsages, LoopEnd.class);
            }
        };
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        properties.put("loopFrequency", String.format("%7.1f", loopFrequency));
        return properties;
    }
}
