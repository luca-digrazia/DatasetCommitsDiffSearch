/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

import com.oracle.truffle.api.profiles.SeparateClassloaderTestRunner;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import org.junit.Assert;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(SeparateClassloaderTestRunner.class)
public class ContentDigestTest {
    @Test
    public void emptyMD2() throws Exception {
        assertDigest(new byte[0], "Empty MD2 digest");
    }

    @Test
    public void hiMD2() throws Exception {
        assertDigest("Hi".getBytes("UTF-8"), "Empty MD2 digest");
    }

    @Test
    public void helloWorldMD2() throws Exception {
        assertDigest("Hello World!".getBytes("UTF-8"), "Empty MD2 digest");
    }

    @Test
    public void minusMD2() throws Exception {
        assertDigest(new byte[]{-75, 119}, "MD2 digest for negative byte");
    }

    @Test
    public void computeMD2s() throws Exception {
        for (int i = 0; i < 100; i++) {
            long seed = System.currentTimeMillis();
            final String msg = "Digest for seed " + seed + " is the same";

            Random random = new Random(seed);

            int len = random.nextInt(2048);
            byte[] arr = new byte[len];
            random.nextBytes(arr);

            assertDigest(arr, msg);
        }
    }

    private void assertDigest(byte[] arr, final String msg) throws NoSuchAlgorithmException {
        byte[] result = MessageDigest.getInstance("MD2").digest(arr);
        String expecting = new BigInteger(1, result).toString(16);

        String own = Content.digest(arr, 0, arr.length);

        Assert.assertEquals(msg, expecting, own);
    }

}
