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
package com.oracle.graal.hotspot.ri;

import com.oracle.max.cri.ri.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.Compiler;

/**
 * Implementation of RiConstantPool for HotSpot.
 */
public class HotSpotConstantPool extends CompilerObject implements RiConstantPool {

    private static final long serialVersionUID = -5443206401485234850L;

    private final HotSpotTypeResolvedImpl type;

    public HotSpotConstantPool(Compiler compiler, HotSpotTypeResolvedImpl type) {
        super(compiler);
        this.type = type;
    }

    @Override
    public Object lookupConstant(int cpi) {
        Object constant = compiler.getCompilerToVM().RiConstantPool_lookupConstant(type, cpi);
        return constant;
    }

    @Override
    public RiSignature lookupSignature(int cpi) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RiMethod lookupMethod(int cpi, int byteCode) {
        return compiler.getCompilerToVM().RiConstantPool_lookupMethod(type, cpi, (byte) byteCode);
    }

    @Override
    public RiType lookupType(int cpi, int opcode) {
        return compiler.getCompilerToVM().RiConstantPool_lookupType(type, cpi);
    }

    @Override
    public RiField lookupField(int cpi, int opcode) {
        return compiler.getCompilerToVM().RiConstantPool_lookupField(type, cpi, (byte) opcode);
    }

    @Override
    public void loadReferencedType(int cpi, int bytecode) {
        compiler.getCompilerToVM().RiConstantPool_loadReferencedType(type, cpi, (byte) bytecode);
    }
}
