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
package com.oracle.svm.agent.jvmti;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.InvokeCFunctionPointer;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.PointerBase;

import com.oracle.svm.jni.nativeapi.JNIFieldId;
import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNINativeInterface;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;

@CStruct(value = "jvmtiInterface_1")
@CContext(JvmtiDirectives.class)
public interface JvmtiInterface extends PointerBase {
    // Checkstyle: stop

    int JVMTI_VERSION_1_2 = 0x30010200;

    @CField("GetJNIFunctionTable")
    GetJNIFunctionTableFunctionPointer GetJNIFunctionTable();

    interface GetJNIFunctionTableFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JvmtiError invoke(JvmtiEnv env, WordPointer functionTablePointer);
    }

    @CField("SetJNIFunctionTable")
    SetJNIFunctionTableFunctionPointer SetJNIFunctionTable();

    interface SetJNIFunctionTableFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JvmtiError invoke(JvmtiEnv env, JNINativeInterface functionTable);
    }

    @CField("Deallocate")
    DeallocateFunctionPointer Deallocate();

    interface DeallocateFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JvmtiError invoke(JvmtiEnv env, PointerBase mem);
    }

    @CField("SetEventCallbacks")
    SetEventCallbacksFunctionPointer SetEventCallbacks();

    interface SetEventCallbacksFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JvmtiError invoke(JvmtiEnv env, JvmtiEventCallbacks callbacks, int sizeOfCallbacks);
    }

    @CField("SetEventNotificationMode")
    SetEventNotificationModeFunctionPointer SetEventNotificationMode();

    interface SetEventNotificationModeFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JvmtiError invoke(JvmtiEnv env, JvmtiEventMode mode, JvmtiEvent type, JNIObjectHandle thread);
    }

    @CField("GetStackTrace")
    GetStackTraceFunctionPointer GetStackTrace();

    interface GetStackTraceFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JvmtiError invoke(JvmtiEnv env, JNIObjectHandle thread, int startDepth, int maxFrameCount, JvmtiFrameInfo frameBuffer, CIntPointer countPtr);
    }

    @CField("GetMethodDeclaringClass")
    GetMethodDeclaringClassFunctionPointer GetMethodDeclaringClass();

    interface GetMethodDeclaringClassFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JvmtiError invoke(JvmtiEnv env, JNIMethodId method, WordPointer declaringClassPtr);
    }

    @CField("GetCapabilities")
    CapabilitiesFunctionPointer GetCapabilities();

    @CField("AddCapabilities")
    CapabilitiesFunctionPointer AddCapabilities();

    interface CapabilitiesFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JvmtiError invoke(JvmtiEnv env, JvmtiCapabilities capabilitiesPtr);
    }

    @CField("SetBreakpoint")
    SetBreakpointFunctionPointer SetBreakpoint();

    interface SetBreakpointFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JvmtiError invoke(JvmtiEnv env, JNIMethodId method, long location);
    }

    @CField("GetLocalObject")
    GetLocalFunctionPointer GetLocalObject();

    @CField("GetLocalInt")
    GetLocalFunctionPointer GetLocalInt();

    interface GetLocalFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JvmtiError invoke(JvmtiEnv env, JNIObjectHandle thread, int depth, int slot, PointerBase valuePtr);
    }

    @CField("GetClassLoader")
    GetClassLoaderFunctionPointer GetClassLoader();

    interface GetClassLoaderFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JvmtiError invoke(JvmtiEnv env, JNIObjectHandle clazz, PointerBase classLoaderPtr);
    }

    @CField("GetMethodName")
    GetMethodNameFunctionPointer GetMethodName();

    interface GetMethodNameFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JvmtiError invoke(JvmtiEnv env, JNIMethodId method, CCharPointerPointer namePtr, CCharPointerPointer signature, CCharPointerPointer genericPtr);
    }

    @CField("GetFieldDeclaringClass")
    GetFieldDeclaringClassFunctionPointer GetFieldDeclaringClass();

    interface GetFieldDeclaringClassFunctionPointer extends CFunctionPointer {
        @InvokeCFunctionPointer
        JvmtiError invoke(JvmtiEnv env, JNIObjectHandle klass, JNIFieldId method, WordPointer declaringClassPtr);
    }
}
