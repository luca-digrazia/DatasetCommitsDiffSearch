/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.gen.cisc.amd64;

import java.util.*;

import com.sun.max.asm.gen.cisc.x86.*;
import com.sun.max.lang.*;

/**
 */
public final class AMD64Assembly extends X86Assembly<AMD64Template> {

    private AMD64Assembly() {
        super(ISA.AMD64, AMD64Template.class);
    }

    @Override
    protected List<AMD64Template> createTemplates() {
        final AMD64TemplateCreator creator = new AMD64TemplateCreator();
        creator.createTemplates(new OneByteOpcodeMap());
        creator.createTemplates(new TwoByteOpcodeMap());
        creator.createTemplates(new FloatingPointOpcodeMap(this));
        return creator.templates();
    }

    public static final AMD64Assembly ASSEMBLY = new AMD64Assembly();
}
