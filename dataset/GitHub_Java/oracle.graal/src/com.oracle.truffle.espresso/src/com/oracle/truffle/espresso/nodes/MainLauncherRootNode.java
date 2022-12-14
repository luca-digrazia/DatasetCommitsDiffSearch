/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

public class MainLauncherRootNode extends RootNode {

    private final MethodInfo main;

    public MainLauncherRootNode(EspressoLanguage language, MethodInfo main) {
        super(language);
        this.main = main;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        try {
            assert frame.getArguments().length == 0;
            EspressoContext context = main.getContext();
            // No var-args here, pull parameters from the context.
            return main.getCallTarget().call((Object) toGuestArguments(context, context.getMainArguments()));
        } catch (EspressoException wrapped) {
            throw wrapped;
        } catch (Throwable throwable) {
            // Non-espresso exceptions cannot escape to the guest.
            // throw EspressoError.shouldNotReachHere();
            throw new RuntimeException(throwable);
        }
    }

    private static StaticObject toGuestArguments(EspressoContext context, String... args) {
        Meta meta = context.getMeta();
        return (StaticObject) meta.STRING.allocateArray(args.length, i -> meta.toGuest(args[i]));
    }
}
