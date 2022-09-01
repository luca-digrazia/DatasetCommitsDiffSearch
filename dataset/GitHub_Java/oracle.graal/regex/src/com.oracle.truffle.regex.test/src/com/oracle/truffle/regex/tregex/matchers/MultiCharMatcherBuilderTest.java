/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.matchers;

import org.junit.Test;

public class MultiCharMatcherBuilderTest extends CharMatcherTest {

    @Test
    public void testInverseSingle() {
        char[] in = {0, 1, 255, Character.MAX_VALUE - 1, Character.MAX_VALUE};
        char[][] out = new char[][]{
                        {1, Character.MAX_VALUE},
                        {0, 0, 2, Character.MAX_VALUE},
                        {0, 254, 256, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE - 1},
        };
        for (int i = 0; i < in.length; i++) {
            checkInverse(single(in[i]), out[i]);
        }
    }

    @Test
    public void testIntersectionSingle() {
        char[] inA = {
                        0,
                        0,
                        0,
                        255,
                        255,
                        Character.MAX_VALUE - 1,
                        Character.MAX_VALUE
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 1},
                        {0, 0, 2, Character.MAX_VALUE},
                        {0, 254, 256, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE}
        };
        char[][] out = {
                        {},
                        {0, 0},
                        {0, 0},
                        {255, 255},
                        {},
                        {},
                        {Character.MAX_VALUE, Character.MAX_VALUE},
        };
        for (int i = 0; i < inA.length; i++) {
            checkIntersection(single(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testSubtractSingle() {
        char[] inA = {
                        0,
                        0,
                        0,
                        255,
                        255,
                        Character.MAX_VALUE - 1,
                        Character.MAX_VALUE
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 1},
                        {0, 0, 2, Character.MAX_VALUE},
                        {0, 254, 256, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE}
        };
        char[][] out = {
                        {0, 0},
                        {},
                        {},
                        {},
                        {255, 255},
                        {Character.MAX_VALUE - 1, Character.MAX_VALUE - 1},
                        {},
        };
        for (int i = 0; i < inA.length; i++) {
            checkSubtraction(single(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testUnionSingle() {
        char[] inA = {
                        0,
                        0,
                        0,
                        255,
                        255,
                        Character.MAX_VALUE - 1,
                        Character.MAX_VALUE,
                        255,
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 1},
                        {0, 0, 2, Character.MAX_VALUE},
                        {0, 254, 256, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE},
                        {142, 150, 190, 200, 300, 340}
        };
        char[][] out = {
                        {0, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 1},
                        {0, 0, 2, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE},
                        {142, 150, 190, 200, 255, 255, 300, 340}
        };
        for (int i = 0; i < inA.length; i++) {
            checkUnion(single(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testInverseSingleRange() {
        char[][] in = {
                        {0, 10},
                        {1, 10},
                        {200, 400},
                        {1000, Character.MAX_VALUE - 1},
                        {1000, Character.MAX_VALUE}
        };
        char[][] out = {
                        {11, Character.MAX_VALUE},
                        {0, 0, 11, Character.MAX_VALUE},
                        {0, 199, 401, Character.MAX_VALUE},
                        {0, 999, Character.MAX_VALUE, Character.MAX_VALUE},
                        {0, 999},
        };
        for (int i = 0; i < in.length; i++) {
            checkInverse(range(in[i]), out[i]);
        }
    }

    @Test
    public void testIntersectionSingleRange() {
        char[][] inA = {
                        {0, 10},
                        {1, 10},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {1000, Character.MAX_VALUE - 1},
                        {1000, Character.MAX_VALUE}
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, Character.MAX_VALUE},
                        {0, 199, 401, Character.MAX_VALUE},
                        {0, 200},
                        {200, 200},
                        {400, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {0, 254, 256, Character.MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE - 1}
        };
        char[][] out = {
                        {1, 10},
                        {1, 10},
                        {},
                        {},
                        {},
                        {200, 200},
                        {200, 200},
                        {400, 400},
                        {200, 300},
                        {200, 300},
                        {300, 400},
                        {300, 400},
                        {200, 254, 256, 400},
                        {200, 250, 300, 310, 350, 400},
                        {1000, Character.MAX_VALUE - 2},
                        {1000, Character.MAX_VALUE - 1},
        };
        for (int i = 0; i < inA.length; i++) {
            checkIntersection(range(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testSubtractSingleRange() {
        char[][] inA = {
                        {0, 10},
                        {1, 10},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {1000, Character.MAX_VALUE - 1},
                        {1000, Character.MAX_VALUE}
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, Character.MAX_VALUE},
                        {0, 199, 401, Character.MAX_VALUE},
                        {0, 200},
                        {400, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {0, 254, 256, Character.MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE - 1}
        };
        char[][] out = {
                        {0, 0},
                        {},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {201, 400},
                        {200, 399},
                        {301, 400},
                        {301, 400},
                        {200, 299},
                        {200, 299},
                        {255, 255},
                        {251, 299, 311, 349},
                        {Character.MAX_VALUE - 1, Character.MAX_VALUE - 1},
                        {Character.MAX_VALUE, Character.MAX_VALUE},
        };
        for (int i = 0; i < inA.length; i++) {
            checkSubtraction(range(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testUnionSingleRange() {
        char[][] inA = {
                        {0, 10},
                        {1, 10},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {1000, Character.MAX_VALUE - 1},
                        {1000, Character.MAX_VALUE}
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, Character.MAX_VALUE},
                        {0, 199, 401, Character.MAX_VALUE},
                        {0, 200},
                        {400, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {0, 254, 256, Character.MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {0, 98, 100, 250, 300, 310, 350, 500, 502, 2000},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE - 1}
        };
        char[][] out = {
                        {0, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 400},
                        {200, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 400},
                        {200, Character.MAX_VALUE},
                        {0, 400},
                        {200, 400},
                        {200, 500},
                        {200, 400},
                        {0, Character.MAX_VALUE},
                        {100, 500},
                        {0, 98, 100, 500, 502, 2000},
                        {0, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE},
        };
        for (int i = 0; i < inA.length; i++) {
            checkUnion(range(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testInverseMultiRange() {
        char[][] in = {
                        {0, 10, 1000, 2000},
                        {1, 10, 1000, 2000},
                        {200, 400, 500, 600},
                        {200, 400, 1000, Character.MAX_VALUE - 1},
                        {0, 10, 1000, Character.MAX_VALUE}
        };
        char[][] out = {
                        {11, 999, 2001, Character.MAX_VALUE},
                        {0, 0, 11, 999, 2001, Character.MAX_VALUE},
                        {0, 199, 401, 499, 601, Character.MAX_VALUE},
                        {0, 199, 401, 999, Character.MAX_VALUE, Character.MAX_VALUE},
                        {11, 999},
        };
        for (int i = 0; i < in.length; i++) {
            checkInverse(multi(in[i]), out[i]);
        }
    }

    @Test
    public void testIntersectionMultiRange() {
        char[][] inA = {
                        {0, 10, 200, 400},
                        {1, 10, 200, 400},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800}
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, 599},
                        {801, Character.MAX_VALUE},
                        {0, 199, 401, 599, 801, Character.MAX_VALUE},
                        {0, 200},
                        {800, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {300, 700},
                        {300, 450, 500, 700},
                        {100, 250, 300, 450, 500, 700, 750, 900},
                        {0, 254, 256, Character.MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {300, 300, 500, 500, 700, 700}
        };
        char[][] out = {
                        {1, 10, 200, 400},
                        {1, 10, 200, 400},
                        {},
                        {},
                        {},
                        {},
                        {200, 200},
                        {800, 800},
                        {200, 300},
                        {200, 300},
                        {300, 400},
                        {300, 400},
                        {300, 400, 600, 700},
                        {300, 400, 600, 700},
                        {200, 250, 300, 400, 600, 700, 750, 800},
                        {200, 254, 256, 400, 600, 800},
                        {200, 250, 300, 310, 350, 400},
                        {300, 300, 700, 700}
        };
        for (int i = 0; i < inA.length; i++) {
            checkIntersection(multi(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testSubtractMultiRange() {
        char[][] inA = {
                        {0, 10, 200, 400},
                        {1, 10, 200, 400},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800}
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, 599},
                        {801, Character.MAX_VALUE},
                        {0, 199, 401, 599, 801, Character.MAX_VALUE},
                        {0, 200},
                        {800, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {300, 700},
                        {300, 450, 500, 700},
                        {100, 250, 300, 450, 500, 700, 750, 900},
                        {0, 254, 256, Character.MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {300, 300, 500, 500, 700, 700}
        };
        char[][] out = {
                        {0, 0},
                        {},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {201, 400, 600, 800},
                        {200, 400, 600, 799},
                        {301, 400, 600, 800},
                        {301, 400, 600, 800},
                        {200, 299, 600, 800},
                        {200, 299, 600, 800},
                        {200, 299, 701, 800},
                        {200, 299, 701, 800},
                        {251, 299, 701, 749},
                        {255, 255},
                        {251, 299, 311, 349, 600, 800},
                        {200, 299, 301, 400, 600, 699, 701, 800}
        };
        for (int i = 0; i < inA.length; i++) {
            checkSubtraction(multi(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testUnionMultiRange() {
        char[][] inA = {
                        {0, 10, 200, 400},
                        {1, 10, 200, 400},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800}
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, 599},
                        {801, Character.MAX_VALUE},
                        {0, 199, 401, 599, 801, Character.MAX_VALUE},
                        {0, 200},
                        {800, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {300, 700},
                        {300, 450, 500, 700},
                        {100, 250, 300, 450, 500, 700, 750, 900},
                        {0, 254, 256, Character.MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {300, 300, 500, 500, 700, 700}
        };
        char[][] out = {
                        {0, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 400, 600, 800},
                        {200, 800},
                        {200, 400, 600, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 400, 600, 800},
                        {200, 400, 600, Character.MAX_VALUE},
                        {0, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 500, 600, 800},
                        {200, 400, 600, 800},
                        {200, 800},
                        {200, 450, 500, 800},
                        {100, 450, 500, 900},
                        {0, Character.MAX_VALUE},
                        {100, 500, 600, 800},
                        {200, 400, 500, 500, 600, 800}
        };
        for (int i = 0; i < inA.length; i++) {
            checkUnion(multi(inA[i]), multi(inB[i]), out[i]);
        }
    }
}
