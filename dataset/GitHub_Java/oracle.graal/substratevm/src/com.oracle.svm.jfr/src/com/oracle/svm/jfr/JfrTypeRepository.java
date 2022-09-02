/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jfr;

import java.io.IOException;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.jfr.traceid.JfrTraceId;
import com.oracle.svm.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.jfr.traceid.JfrTraceIdLoadBarrier;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.thread.VMOperation;

public class JfrTypeRepository implements JfrRepository {
    private final JfrSymbolRepository symbolRepo;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrTypeRepository(JfrSymbolRepository symbolRepo) {
        this.symbolRepo = symbolRepo;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getClassId(Class<?> clazz) {
        return JfrTraceId.load(clazz);
    }

    @Override
    public void write(JfrChunkWriter writer) throws IOException {
        assert VMOperation.isInProgressAtSafepoint();
        writer.writeCompressedLong(JfrTypes.Class.getId());
        writer.writeCompressedLong(JfrTraceIdLoadBarrier.classCount(JfrTraceIdEpoch.getInstance().previousEpoch()));

        JfrTraceIdLoadBarrier.ClassConsumer kc = aClass -> writeClass(aClass, writer);
        JfrTraceIdLoadBarrier.doClasses(kc, JfrTraceIdEpoch.getInstance().previousEpoch());
    }

    private void writeClass(Class<?> clazz, JfrChunkWriter writer) {
        try {
            writer.writeCompressedLong(0L); // classloader
            writer.writeCompressedLong(symbolRepo.getSymbolId(clazz));
            writer.writeCompressedLong(0); // package id
            writer.writeCompressedLong(clazz.getModifiers());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean hasItems() {
        return JfrTraceIdLoadBarrier.classCount(JfrTraceIdEpoch.getInstance().previousEpoch()) > 0;
    }
}
