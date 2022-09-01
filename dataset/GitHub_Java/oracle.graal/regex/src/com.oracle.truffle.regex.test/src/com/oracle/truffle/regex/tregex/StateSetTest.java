/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.regex.tregex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.regex.tregex.automaton.StateIndex;
import com.oracle.truffle.regex.tregex.automaton.StateSet;

public class StateSetTest {

    private static final int INDEX_SIZE = 0xFF;
    static final int MAX_SMALL_STATE_INDEX_SIZE = 64;
    private static final int SWITCH_TO_BITSET_THRESHOLD = 4;

    private ShortStateIndex index;
    private ShortStateIndex smallIndex;
    private List<ShortState> tooManyForStateList;

    @Before
    public void setUp() {
        index = new ShortStateIndex(INDEX_SIZE);
        smallIndex = new ShortStateIndex(MAX_SMALL_STATE_INDEX_SIZE);
        tooManyForStateList = new ArrayList<>();
        for (int i = 1; i <= SWITCH_TO_BITSET_THRESHOLD + 1; i++) {
            tooManyForStateList.add(new ShortState(i));
        }
    }

    private StateSetChecker<ShortState> stateSetCreate() {
        return new StateSetChecker<>(StateSet.create(index), StateSet.create(smallIndex));
    }

    private StateSet<ShortState> bitSet(int... elems) {
        StateSetChecker<ShortState> result = stateSetCreate();

        // force the use of a bit set instead of a state list
        result.addAll(tooManyForStateList);
        result.removeAll(tooManyForStateList);

        for (int elem : elems) {
            result.add(new ShortState(elem));
        }

        return result;
    }

    private StateSet<ShortState> stateList(int... elems) {
        StateSet<ShortState> result = stateSetCreate();

        assert elems.length <= SWITCH_TO_BITSET_THRESHOLD;

        for (int elem : elems) {
            result.add(new ShortState(elem));
        }

        return result;
    }

    @Test
    public void consistentHashCodes() {
        StateSet<ShortState> usingBitSet = bitSet(1, 2, 3, 4);
        StateSet<ShortState> usingStateList = stateList(1, 2, 3, 4);

        Assert.assertEquals(usingBitSet, usingStateList);

        Assert.assertEquals("hash codes of equal StateSets should be equal", usingBitSet.hashCode(), usingStateList.hashCode());
    }

    private static class ShortState {

        private final short id;

        ShortState(int id) {
            this.id = (short) id;
        }

        public short getId() {
            return id;
        }
    }

    private static class ShortStateIndex implements StateIndex<ShortState> {

        private final ShortState[] index;

        ShortStateIndex(int size) {
            index = new ShortState[size];
            Arrays.setAll(index, ShortState::new);
        }

        @Override
        public int getNumberOfStates() {
            return index.length;
        }

        @Override
        public short getId(ShortState state) {
            return state.getId();
        }

        @Override
        public ShortState getState(int id) {
            return index[id];
        }
    }
}
