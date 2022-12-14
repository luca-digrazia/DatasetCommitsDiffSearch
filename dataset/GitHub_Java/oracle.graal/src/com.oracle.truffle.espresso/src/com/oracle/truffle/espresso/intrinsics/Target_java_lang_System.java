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

package com.oracle.truffle.espresso.intrinsics;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Properties;

import com.oracle.truffle.espresso.bytecode.InterpreterToVM;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;
import com.oracle.truffle.espresso.runtime.Utils;

@EspressoIntrinsics
public class Target_java_lang_System {

    @Intrinsic
    public static void exit(int status) {
        // TODO(peterssen): Use TruffleException.
        System.exit(status);
    }

    @Intrinsic
    public static @Type(Properties.class) StaticObject initProperties(@Type(Properties.class) StaticObject props) {
        EspressoContext context = Utils.getContext();
        final String[] importedProps = new String[]{
                        "java.version",
                        "java.vendor",
                        "java.vendor.url",
                        "java.home",
                        "java.class.version",
                        "java.class.path",
                        "os.name",
                        "os.arch",
                        "os.version",
                        "file.separator",
                        "path.separator",
                        "line.separator",
                        "user.name",
                        "user.home",
                        "user.dir",
                        // TODO(peterssen): Parse the boot classpath from arguments.
                        "sun.boot.class.path",

                        // Needed during initSystemClass to initialize props.
                        "file.encoding"
        };

        MethodInfo setProperty = props.getKlass().findDeclaredMethod("setProperty",
                        Object.class, String.class, String.class);

        for (String prop : importedProps) {

            StaticObject guestPropKey = context.getMeta().toGuest(prop);
            StaticObject guestPropValue;

            // Inject guest classpath.
            if (prop.equals("java.class.path")) {
                guestPropValue = context.getMeta().toGuest(context.getClasspath().toString());
            } else {
                guestPropValue = context.getMeta().toGuest(System.getProperty(prop));
            }

            setProperty.getCallTarget().call(props, guestPropKey, guestPropValue);
        }

        return props;
    }

    @Intrinsic
    public static void setIn0(@Type(InputStream.class) StaticObjectImpl in) {
        Utils.getContext().getMeta().knownKlass(System.class)
                .staticField("in")
                .set(in);
    }

    @Intrinsic
    public static void setOut0(@Type(PrintStream.class) StaticObject out) {
        Utils.getContext().getMeta().knownKlass(System.class)
                .staticField("out")
                .set(out);
    }

    @Intrinsic
    public static void setErr0(@Type(PrintStream.class) StaticObject err) {
        Utils.getContext().getMeta().knownKlass(System.class)
                .staticField("err")
                .set(err);
    }

    @Intrinsic
    public static void arraycopy(Object src, int srcPos,
                    Object dest, int destPos,
                    int length) {
        try {
            if (src instanceof StaticObjectArray && dest instanceof StaticObjectArray) {
                System.arraycopy(((StaticObjectArray) src).getWrapped(), srcPos, ((StaticObjectArray) dest).getWrapped(), destPos, length);
            } else {
                assert src.getClass().isArray();
                assert dest.getClass().isArray();
                System.arraycopy(src, srcPos, dest, destPos, length);
            }
        } catch (Throwable e) {
            // TODO(peterssen): Throw guest exception.
            throw e;
        }
    }

    @Intrinsic
    public static long currentTimeMillis() {
        // TODO(peterssen): Speed up time.
        return System.currentTimeMillis();
    }

    @Intrinsic
    public static long nanoTime() {
        return System.nanoTime();
    }

    @Intrinsic
    public static void registerNatives() {
        /* nop */
    }

    @Intrinsic
    public static void loadLibrary(@Type(String.class) StaticObject libname) {
        /* nop */
    }
}
