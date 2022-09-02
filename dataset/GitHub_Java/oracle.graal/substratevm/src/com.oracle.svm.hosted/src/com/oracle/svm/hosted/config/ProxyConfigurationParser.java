/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.config;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.jdk.proxy.DynamicProxyRegistry;
import com.oracle.svm.core.util.json.JSONParser;
import com.oracle.svm.core.util.json.JSONParserException;
import com.oracle.svm.hosted.ImageClassLoader;

// Checkstyle: allow reflection

/**
 * Parses JSON describing lists of interfaces and register them in the {@link DynamicProxyRegistry}.
 */
public final class ProxyConfigurationParser extends ConfigurationParser {
    private final ImageClassLoader classLoader;
    private final DynamicProxyRegistry dynamicProxyRegistry;

    public ProxyConfigurationParser(ImageClassLoader classLoader, DynamicProxyRegistry dynamicProxyRegistry) {
        this.classLoader = classLoader;
        this.dynamicProxyRegistry = dynamicProxyRegistry;
    }

    @Override
    public void parseAndRegister(Reader reader) throws IOException {
        JSONParser parser = new JSONParser(reader);
        Object json = parser.parse();
        parseTopLevelArray(asList(json, "first level of document must be an array of interface lists"));
    }

    private void parseTopLevelArray(List<Object> interfaceLists) {
        for (Object interfaceList : interfaceLists) {
            parseInterfaceList(asList(interfaceList, "second level of document must be a lists of objects"));
        }
    }

    private void parseInterfaceList(List<?> data) {
        List<Class<?>> interfaces = new ArrayList<>();

        for (Object value : data) {
            String className = asString(value);

            Class<?> clazz = classLoader.findClassByName(className, false);
            if (clazz == null) {
                throw new JSONParserException("Class " + className + " not found");
            }

            if (!clazz.isInterface()) {
                throw new JSONParserException("The class \"" + className + "\" is not an interface.");
            }

            interfaces.add(clazz);
        }

        /* The interfaces array can be empty. The java.lang.reflect.Proxy API allows it. */
        dynamicProxyRegistry.addProxyClass(interfaces.toArray(new Class<?>[0]));
    }

}
