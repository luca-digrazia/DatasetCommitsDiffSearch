/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.agent.restrict;

import static com.oracle.svm.agent.Support.handles;
import static com.oracle.svm.agent.Support.jniFunctions;
import static com.oracle.svm.agent.Support.toCString;

import java.util.Arrays;

import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;

import com.oracle.svm.agent.Agent;
import com.oracle.svm.configure.config.ProxyConfiguration;
import com.oracle.svm.configure.trace.AccessAdvisor;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

public class ProxyAccessVerifier extends AbstractAccessVerifier {
    private final ProxyConfiguration configuration;

    public ProxyAccessVerifier(ProxyConfiguration configuration, AccessAdvisor advisor) {
        super(advisor);
        this.configuration = configuration;
    }

    public boolean verifyNewProxyInstance(JNIEnvironment env, Object interfaceNames, JNIObjectHandle callerClass) {
        return verifyProxyAccess(env, interfaceNames, callerClass);
    }

    public boolean verifyGetProxyClass(JNIEnvironment env, Object interfaceNames, JNIObjectHandle callerClass) {
        return verifyProxyAccess(env, interfaceNames, callerClass);
    }

    private boolean verifyProxyAccess(JNIEnvironment env, Object interfaceNames, JNIObjectHandle callerClass) {
        if (shouldApproveWithoutChecks(env, callerClass)) {
            return true;
        }
        String interfaceString = "(unknown)";
        if (interfaceNames instanceof String[]) {
            if (configuration.contains(Arrays.asList((String[]) interfaceNames))) {
                return true;
            }
            interfaceString = Arrays.toString((String[]) interfaceNames);
        }
        try (CCharPointerHolder message = toCString(Agent.MESSAGE_PREFIX + "configuration does not permit proxy class for interfaces: " + interfaceString)) {
            beforeThrow(message);
            jniFunctions().getThrowNew().invoke(env, handles().javaLangSecurityException, message.get());
        }
        return false;
    }

    private static void beforeThrow(@SuppressWarnings("unused") CCharPointerHolder message) {
        // System.err.println(Agent.MESSAGE_PREFIX + fromCString(message.get()));
    }
}
