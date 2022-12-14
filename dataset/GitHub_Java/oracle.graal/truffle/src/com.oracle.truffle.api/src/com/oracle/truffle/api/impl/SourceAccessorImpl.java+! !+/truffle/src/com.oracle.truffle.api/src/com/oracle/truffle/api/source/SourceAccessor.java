/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.impl;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleOptions;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

public final class SourceAccessorImpl extends SourceAccessor {

    @Override
    protected Collection<ClassLoader> loaders() {
        return TruffleLocator.loaders();
    }

    @Override
    protected boolean checkAOT() {
        return TruffleOptions.AOT;
    }

    @Override
    protected void assertNeverPartOfCompilation(String msg) {
        CompilerAsserts.neverPartOfCompilation(msg);
    }

    @Override
    protected boolean checkTruffleFile(File file) {
        return file instanceof TruffleFileFileAdapter;
    }

    @Override
    protected byte[] truffleFileContent(File file) throws IOException {
        assert file instanceof TruffleFileFileAdapter : "File must be " + TruffleFileFileAdapter.class.getSimpleName();
        final TruffleFile tf = ((TruffleFileFileAdapter) file).getTruffleFile();
        return tf.readAllBytes();
    }

    public static File asFile(TruffleFile truffleFile) {
        return new TruffleFileFileAdapter(truffleFile);
    }
}
