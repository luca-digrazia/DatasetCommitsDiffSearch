/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.ClassfileStream;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.CodeAttribute;
import com.oracle.truffle.espresso.classfile.attributes.LineNumberTableAttribute;
import com.oracle.truffle.espresso.classfile.attributes.Local;
import com.oracle.truffle.espresso.classfile.attributes.LocalVariableTable;
import com.oracle.truffle.espresso.classfile.constantpool.PoolConstant;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.jdwp.api.ErrorCodes;
import com.oracle.truffle.espresso.jdwp.api.Ids;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

public final class ClassRedefinition {

    @CompilationFinal static volatile RedefineAssumption current = new RedefineAssumption();
    private static final RedefinitionSupport REDEFINITION_SUPPORT = RedefinitionSupport.REMOVE_METHOD;

    private static final Object redefineLock = new Object();
    private static volatile boolean locked = false;
    private static Thread redefineThread = null;

    private enum RedefinitionSupport {
        METHOD_BODY,
        ADD_METHOD,
        REMOVE_METHOD,
        ARBITRARY
    }

    enum ClassChange {
        NO_CHANGE,
        CLASS_NAME_CHANGED,
        METHOD_BODY_CHANGE,
        ADD_METHOD,
        SCHEMA_CHANGE,
        HIERARCHY_CHANGE,
        REMOVE_METHOD,
        CLASS_MODIFIERS_CHANGE,
        CONSTANT_POOL_CHANGE,
        NEW_CLASS,
        INVALID;
    }

    public static void lock() {
        synchronized (redefineLock) {
            check();
            locked = true;
        }
    }

    public static void unlock() {
        synchronized (redefineLock) {
            check();
            locked = false;
            redefineLock.notifyAll();
        }
    }

    public static void begin() {
        synchronized (redefineLock) {
            while (locked) {
                try {
                    redefineLock.wait();
                } catch (InterruptedException e) {
                    Thread.interrupted();
                }
            }
            // the redefine thread is privileged
            redefineThread = Thread.currentThread();
            locked = true;
            current.assumption.invalidate();
        }
    }

    public static void end() {
        synchronized (redefineLock) {
            locked = false;
            current = new RedefineAssumption();
            redefineThread = null;
            redefineLock.notifyAll();
        }
    }

    private static class RedefineAssumption {
        private final Assumption assumption = Truffle.getRuntime().createAssumption();
    }

    public static void check() {
        RedefineAssumption ra = current;
        if (!ra.assumption.isValid()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            if (redefineThread == Thread.currentThread()) {
                // let the redefine thread pass
                return;
            }
            // block until redefinition is done
            synchronized (redefineLock) {
                while (locked) {
                    try {
                        redefineLock.wait();
                    } catch (InterruptedException e) {
                        Thread.interrupted();
                    }
                }
            }
            // re-check in case a new redefinition was kicked off
            check();
        }
    }

    public static List<ChangePacket> detectClassChanges(HotSwapClassInfo[] classInfos, EspressoContext context) {
        List<ChangePacket> result = new ArrayList<>(classInfos.length);
        for (HotSwapClassInfo hotSwapInfo : classInfos) {
            KlassRef klass = hotSwapInfo.getKlass();
            if (klass == null) {
                // New anonymous inner class
                result.add(new ChangePacket(hotSwapInfo, ClassChange.NEW_CLASS));
                continue;
            }
            byte[] bytes = hotSwapInfo.getBytes();
            ParserKlass parserKlass = null;
            ParserKlass newParserKlass = null;
            ClassChange classChange;
            DetectedChange detectedChange = new DetectedChange();
            if (klass instanceof ObjectKlass) {
                parserKlass = ClassfileParser.parse(new ClassfileStream(bytes, null), "L" + hotSwapInfo.getName() + ";", null, context);
                if (hotSwapInfo.isPatched()) {
                    byte[] patched = hotSwapInfo.getPatchedBytes();
                    newParserKlass = parserKlass;
                    // we detect changes against the patched bytecode
                    parserKlass = ClassfileParser.parse(new ClassfileStream(patched, null), "L" + hotSwapInfo.getNewName() + ";", null, context);
                }
                classChange = detectClassChanges(parserKlass, (ObjectKlass) klass, detectedChange, newParserKlass);
            } else {
                // array or primitive klass, should never happen
                classChange = ClassChange.INVALID;
            }
            result.add(new ChangePacket(hotSwapInfo, newParserKlass != null ? newParserKlass : parserKlass, classChange, detectedChange));
        }
        return result;
    }

    public static int redefineClass(ChangePacket packet, Ids<Object> ids, EspressoContext context, List<ObjectKlass> refreshSubClasses) {
        try {
            switch (packet.classChange) {
                case METHOD_BODY_CHANGE:
                case CONSTANT_POOL_CHANGE:
                case CLASS_NAME_CHANGED:
                    return doRedefineClass(packet, ids, context, refreshSubClasses);
                case ADD_METHOD:
                    if (isAddMethodSupported()) {
                        return doRedefineClass(packet, ids, context, refreshSubClasses);
                    } else {
                        return ErrorCodes.ADD_METHOD_NOT_IMPLEMENTED;
                    }
                case REMOVE_METHOD:
                    if (isRemoveMethodSupported()) {
                        return doRedefineClass(packet, ids, context, refreshSubClasses);
                    } else {
                        return ErrorCodes.DELETE_METHOD_NOT_IMPLEMENTED;
                    }
                case SCHEMA_CHANGE:
                    if (isArbitraryChangesSupported()) {
                        return doRedefineClass(packet, ids, context, refreshSubClasses);
                    } else {
                        return ErrorCodes.SCHEMA_CHANGE_NOT_IMPLEMENTED;
                    }
                case CLASS_MODIFIERS_CHANGE:
                    if (isArbitraryChangesSupported()) {
                        return doRedefineClass(packet, ids, context, refreshSubClasses);
                    } else {
                        return ErrorCodes.CLASS_MODIFIERS_CHANGE_NOT_IMPLEMENTED;
                    }
                case HIERARCHY_CHANGE:
                    if (isArbitraryChangesSupported()) {
                        return doRedefineClass(packet, ids, context, refreshSubClasses);
                    } else {
                        return ErrorCodes.HIERARCHY_CHANGE_NOT_IMPLEMENTED;
                    }
                case NEW_CLASS:
                    ClassInfo classInfo = packet.info;

                    // if there is a currently loaded class under that name
                    // we have to replace that in the class loader registry etc.
                    // otherwise, don't eagerly define the new class
                    Symbol<Symbol.Type> type = context.getTypes().fromName(classInfo.getName());
                    ClassRegistry classRegistry = context.getRegistries().getClassRegistry(classInfo.getClassLoader());
                    Klass loadedKlass = classRegistry.findLoadedKlass(type);
                    if (loadedKlass != null) {
                        // OK, we have to define the new klass instance and
                        // inject it under the existing JDWP ID
                        classRegistry.onInnerClassRemoved(type);
                        ObjectKlass newKlass = classRegistry.defineKlass(type, classInfo.getBytes());
                        packet.info.setKlass(newKlass);
                    }
                    return 0;
                default:
                    return 0;
            }
        } catch (EspressoException ex) {
            // TODO(Gregersen) - return appropriate error code based on the exception type
            // we get from parsing the class file
            return ErrorCodes.INVALID_CLASS_FORMAT;
        }
    }

    private static boolean isArbitraryChangesSupported() {
        return REDEFINITION_SUPPORT == RedefinitionSupport.ARBITRARY;
    }

    private static boolean isAddMethodSupported() {
        return REDEFINITION_SUPPORT == RedefinitionSupport.ADD_METHOD || isRemoveMethodSupported() || isArbitraryChangesSupported();
    }

    private static boolean isRemoveMethodSupported() {
        return REDEFINITION_SUPPORT == RedefinitionSupport.REMOVE_METHOD || isArbitraryChangesSupported();
    }

    // detect all types of class changes, but return early when a change that require arbitrary
    // changes
    private static ClassChange detectClassChanges(ParserKlass newParserKlass, ObjectKlass oldKlass, DetectedChange collectedChanges, ParserKlass finalParserKlass) {
        ClassChange result = ClassChange.NO_CHANGE;
        ParserKlass oldParserKlass = oldKlass.getLinkedKlass().getParserKlass();
        boolean isPatched = finalParserKlass != null;

        // detect class-level changes
        if (newParserKlass.getFlags() != oldParserKlass.getFlags()) {
            if (isArbitraryChangesSupported()) {
                return ClassChange.CLASS_MODIFIERS_CHANGE;
            } else {
                throw new RedefintionNotSupportedException(ErrorCodes.CLASS_MODIFIERS_CHANGE_NOT_IMPLEMENTED);
            }
        }

        if (!newParserKlass.getSuperKlass().equals(oldParserKlass.getSuperKlass()) || !Arrays.equals(newParserKlass.getSuperInterfaces(), oldParserKlass.getSuperInterfaces())) {
            if (isArbitraryChangesSupported()) {
                return ClassChange.HIERARCHY_CHANGE;
            } else {
                throw new RedefintionNotSupportedException(ErrorCodes.HIERARCHY_CHANGE_NOT_IMPLEMENTED);
            }
        }

        // detect field changes
        Field[] oldFields = oldKlass.getDeclaredFields();
        ParserField[] newFields = newParserKlass.getFields();

        if (oldFields.length != newFields.length) {
            if (isArbitraryChangesSupported()) {
                return ClassChange.SCHEMA_CHANGE;
            } else {
                throw new RedefintionNotSupportedException(ErrorCodes.SCHEMA_CHANGE_NOT_IMPLEMENTED);
            }
        }

        for (int i = 0; i < oldFields.length; i++) {
            Field oldField = oldFields[i];
            // verify that there is a new corresponding field
            boolean found = false;
            for (int j = 0; j < newFields.length; j++) {
                ParserField newField = newFields[j];
                if (isUnchangedField(oldField, newField)) {
                    Matcher matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(oldField.getType().toString());
                    if (isPatched && matcher.matches()) {
                        // special outer pointer in nested anonymous inner classes
                        collectedChanges.addOuterField(oldField);
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (isArbitraryChangesSupported()) {
                    return ClassChange.SCHEMA_CHANGE;
                } else {
                    throw new RedefintionNotSupportedException(ErrorCodes.SCHEMA_CHANGE_NOT_IMPLEMENTED);
                }
            }
        }

        // detect method changes (including constructors)
        ParserMethod[] newParserMethods = newParserKlass.getMethods();
        List<Method> oldMethods = new ArrayList<>(Arrays.asList(oldKlass.getDeclaredMethods()));
        List<ParserMethod> newMethods = new ArrayList<>(Arrays.asList(newParserMethods));
        Map<Method, ParserMethod> bodyChanges = new HashMap<>();
        List<ParserMethod> newSpecialMethods = new ArrayList<>(1);

        boolean constantPoolChanged = false;
        // check constant pool changes. If changed, we have to redefine all methods in the class
        if (!Arrays.equals(oldParserKlass.getConstantPool().getRawBytes(), newParserKlass.getConstantPool().getRawBytes())) {
            constantPoolChanged = true;
        }
        Iterator<Method> oldIt = oldMethods.iterator();
        while (oldIt.hasNext()) {
            Method oldMethod = oldIt.next();
            ParserMethod oldParserMethod = oldMethod.getLinkedMethod().getParserMethod();
            // verify that there is a new corresponding method
            Iterator<ParserMethod> newIt = newMethods.iterator();
            while (newIt.hasNext()) {
                ParserMethod newMethod = newIt.next();
                if (isSameMethod(oldParserMethod, newMethod)) {
                    // detect method changes
                    ClassChange change = detectMethodChanges(oldParserMethod, newMethod);
                    switch (change) {
                        case NO_CHANGE:
                            if (isPatched) {
                                checkForSpecialConstructor(collectedChanges, bodyChanges, newSpecialMethods, oldMethod, oldParserMethod, newMethod);
                                break;
                            }
                            if (constantPoolChanged) {
                                if (isObsolete(oldParserMethod, newMethod, oldParserKlass.getConstantPool(), newParserKlass.getConstantPool())) {
                                    result = ClassChange.CONSTANT_POOL_CHANGE;
                                    collectedChanges.addMethodBodyChange(oldMethod, newMethod);
                                }
                            }
                            break;
                        case METHOD_BODY_CHANGE:
                            result = change;
                            if (isPatched) {
                                checkForSpecialConstructor(collectedChanges, bodyChanges, newSpecialMethods, oldMethod, oldParserMethod, newMethod);
                            } else {
                                collectedChanges.addMethodBodyChange(oldMethod, newMethod);
                            }
                            break;
                        default:
                            return change;
                    }
                    newIt.remove();
                    oldIt.remove();
                    break;
                }
            }
        }
        if (isPatched) {
            ParserMethod[] finalMethods = finalParserKlass.getMethods();
            // lookup the final new method based on the index in the parser method array

            // map found changed methods
            for (Map.Entry<Method, ParserMethod> entry : bodyChanges.entrySet()) {
                Method oldMethod = entry.getKey();
                ParserMethod changed = entry.getValue();
                for (int i = 0; i < newParserMethods.length; i++) {
                    if (newParserMethods[i] == changed) {
                        collectedChanges.addMethodBodyChange(oldMethod, finalMethods[i]);
                        break;
                    }
                }
            }
            // map found new methods
            newMethods.addAll(newSpecialMethods);
            for (ParserMethod changed : newMethods) {
                for (int i = 0; i < newParserMethods.length; i++) {
                    if (newParserMethods[i] == changed) {
                        collectedChanges.addNewMethod(finalMethods[i]);
                        break;
                    }
                }
            }
        } else {
            collectedChanges.addNewMethods(newMethods);
        }

        collectedChanges.addRemovedMethods(oldMethods);

        if (!oldMethods.isEmpty()) {
            result = ClassChange.REMOVE_METHOD;
        } else if (!newMethods.isEmpty()) {
            result = ClassChange.ADD_METHOD;
        }

        if (isPatched) {
            result = ClassChange.CLASS_NAME_CHANGED;
        }

        return result;
    }

    private static void checkForSpecialConstructor(DetectedChange collectedChanges, Map<Method, ParserMethod> bodyChanges, List<ParserMethod> newSpecialMethods,
                    Method oldMethod, ParserMethod oldParserMethod, ParserMethod newMethod) {
        // mark constructors of nested anonymous inner classes
        // if they include an anonymous inner class type parameter
        if (Symbol.Name._init_.equals(oldParserMethod.getName()) && !Symbol.Signature._void.equals(oldParserMethod.getSignature())) {
            // only mark constructors that contain the outer anonymous inner class
            Matcher matcher = InnerClassRedefiner.ANON_INNER_CLASS_PATTERN.matcher(oldParserMethod.getSignature().toString());
            if (matcher.matches()) {
                newSpecialMethods.add(newMethod);
                collectedChanges.addRemovedMethods(oldMethod);
            }
        } else {
            // for class-name patched classes we have to redefine all methods
            bodyChanges.put(oldMethod, newMethod);
        }
    }

    private static boolean isObsolete(ParserMethod oldMethod, ParserMethod newMethod, ConstantPool oldPool, ConstantPool newPool) {
        CodeAttribute oldCodeAttribute = (CodeAttribute) oldMethod.getAttribute(Symbol.Name.Code);
        CodeAttribute newCodeAttribute = (CodeAttribute) newMethod.getAttribute(Symbol.Name.Code);
        if (oldCodeAttribute == null) {
            return newCodeAttribute != null;
        } else if (newCodeAttribute == null) {
            return oldCodeAttribute != null;
        }
        BytecodeStream oldCode = new BytecodeStream(oldCodeAttribute.getOriginalCode());
        BytecodeStream newCode = new BytecodeStream(newCodeAttribute.getOriginalCode());

        return !isSame(oldCode, oldPool, newCode, newPool);
    }

    private static boolean isSame(BytecodeStream oldCode, ConstantPool oldPool, BytecodeStream newCode, ConstantPool newPool) {
        int bci;
        int nextBCI = 0;
        while (nextBCI < oldCode.endBCI()) {
            bci = nextBCI;
            int opcode = oldCode.currentBC(bci);
            nextBCI = oldCode.nextBCI(bci);
            if (opcode == Bytecodes.LDC || opcode == Bytecodes.LDC2_W || opcode == Bytecodes.LDC_W || opcode == Bytecodes.NEW || opcode == Bytecodes.INVOKEDYNAMIC || Bytecodes.isInvoke(opcode)) {
                int oldCPI = oldCode.readCPI(bci);
                PoolConstant oldConstant = oldPool.at(oldCPI);
                int newCPI = newCode.readCPI(bci);
                PoolConstant newConstant = newPool.at(newCPI);
                if (!oldConstant.toString(oldPool).equals(newConstant.toString(newPool))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static ClassChange detectMethodChanges(ParserMethod oldMethod, ParserMethod newMethod) {
        // check method attributes that would constitute a higher-level
        // class redefinition than a method body change
        if (checkAttribute(oldMethod, newMethod, Symbol.Name.RuntimeVisibleTypeAnnotations)) {
            return ClassChange.SCHEMA_CHANGE;
        }

        if (checkAttribute(oldMethod, newMethod, Symbol.Name.RuntimeInvisibleTypeAnnotations)) {
            return ClassChange.SCHEMA_CHANGE;
        }

        if (checkAttribute(oldMethod, newMethod, Symbol.Name.Signature)) {
            return ClassChange.SCHEMA_CHANGE;
        }

        if (checkAttribute(oldMethod, newMethod, Symbol.Name.Exceptions)) {
            return ClassChange.SCHEMA_CHANGE;
        }

        // check code attribute
        CodeAttribute oldCodeAttribute = (CodeAttribute) oldMethod.getAttribute(Symbol.Name.Code);
        CodeAttribute newCodeAttribute = (CodeAttribute) newMethod.getAttribute(Symbol.Name.Code);

        if (oldCodeAttribute == null) {
            return newCodeAttribute != null ? ClassChange.METHOD_BODY_CHANGE : ClassChange.NO_CHANGE;
        } else if (newCodeAttribute == null) {
            return oldCodeAttribute != null ? ClassChange.METHOD_BODY_CHANGE : ClassChange.NO_CHANGE;
        }

        if (!Arrays.equals(oldCodeAttribute.getOriginalCode(), newCodeAttribute.getOriginalCode())) {
            return ClassChange.METHOD_BODY_CHANGE;
        }
        // check line number table
        if (checkLineNumberTable(oldCodeAttribute.getLineNumberTableAttribute(), newCodeAttribute.getLineNumberTableAttribute())) {
            return ClassChange.METHOD_BODY_CHANGE;
        }

        // check local variable table
        if (checkLocalVariableTable(oldCodeAttribute.getLocalvariableTable(), newCodeAttribute.getLocalvariableTable())) {
            return ClassChange.METHOD_BODY_CHANGE;
        }

        // check local variable type table
        if (checkLocalVariableTable(oldCodeAttribute.getLocalvariableTypeTable(), newCodeAttribute.getLocalvariableTypeTable())) {
            return ClassChange.METHOD_BODY_CHANGE;
        }

        return ClassChange.NO_CHANGE;
    }

    private static boolean checkLineNumberTable(LineNumberTableAttribute table1, LineNumberTableAttribute table2) {
        LineNumberTableAttribute.Entry[] oldEntries = table1.getEntries();
        LineNumberTableAttribute.Entry[] newEntries = table2.getEntries();

        if (oldEntries.length != newEntries.length) {
            return true;
        }

        for (int i = 0; i < oldEntries.length; i++) {
            LineNumberTableAttribute.Entry oldEntry = oldEntries[i];
            LineNumberTableAttribute.Entry newEntry = newEntries[i];
            if (oldEntry.getLineNumber() != newEntry.getLineNumber() || oldEntry.getBCI() != newEntry.getBCI()) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkLocalVariableTable(LocalVariableTable table1, LocalVariableTable table2) {
        Local[] oldLocals = table1.getLocals();
        Local[] newLocals = table2.getLocals();

        if (oldLocals.length != newLocals.length) {
            return true;
        }

        for (int i = 0; i < oldLocals.length; i++) {
            Local oldLocal = oldLocals[i];
            Local newLocal = newLocals[i];
            if (!oldLocal.getNameAsString().equals(newLocal.getNameAsString()) || oldLocal.getSlot() != newLocal.getSlot() || oldLocal.getStartBCI() != newLocal.getStartBCI() ||
                            oldLocal.getEndBCI() != newLocal.getEndBCI()) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkAttribute(ParserMethod oldMethod, ParserMethod newMethod, Symbol<Symbol.Name> name) {
        Attribute oldAttribute = oldMethod.getAttribute(name);
        Attribute newAttribute = newMethod.getAttribute(name);
        if ((oldAttribute == null || newAttribute == null)) {
            if (oldAttribute != null || newAttribute != null) {
                return true;
            } // else both null, so no change. Move on!
        } else if (!Arrays.equals(oldAttribute.getData(), newAttribute.getData())) {
            return true;
        }
        return false;
    }

    private static boolean isSameMethod(ParserMethod oldMethod, ParserMethod newMethod) {
        return oldMethod.getName().equals(newMethod.getName()) &&
                        oldMethod.getSignature().equals(newMethod.getSignature()) &&
                        oldMethod.getFlags() == newMethod.getFlags();
    }

    private static boolean isUnchangedField(Field oldField, ParserField newField) {
        boolean same = oldField.getName().equals(newField.getName()) && oldField.getType().equals(newField.getType()) && oldField.getModifiers() == newField.getFlags();

        if (same) {
            // check field attributes
            Attribute[] oldAttributes = oldField.getAttributes();
            Attribute[] newAttributes = newField.getAttributes();

            if (oldAttributes.length != newAttributes.length) {
                return false;
            }

            for (Attribute oldAttribute : oldAttributes) {
                boolean found = false;
                for (Attribute newAttribute : newAttributes) {
                    if (oldAttribute.getName().equals(newAttribute.getName()) && Arrays.equals(oldAttribute.getData(), newAttribute.getData())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static int doRedefineClass(ChangePacket packet, Ids<Object> ids, EspressoContext context, List<ObjectKlass> refreshSubClasses) {
        ObjectKlass oldKlass = packet.info.getKlass();
        ClassRegistry classRegistry = context.getRegistries().getClassRegistry(packet.info.getClassLoader());
        if (packet.info.isRenamed()) {
            // renaming a class is done by
            // 1. Rename the 'name' and 'type' Symbols in the Klass
            // 2. Update the loaded class cache in the associated ClassRegistry
            // 3. Set the guest language java.lang.Class#name field to null
            // 4. update the JDWP refType ID for the klass instance
            // 5. replace/record a classloader constraint for the new type and klass combination

            Symbol<Symbol.Type> type = context.getTypes().fromName(packet.info.getName());
            Klass loadedKlass = classRegistry.findLoadedKlass(type);
            if (loadedKlass != null) {
                context.getRegistries().removeUnloadedKlassConstraint(loadedKlass, type);
            }
            oldKlass.patchClassName(packet.info.getName());
            classRegistry.onClassRenamed(oldKlass, packet.info.getName());

            InterpreterToVM.setFieldObject(StaticObject.NULL, oldKlass.mirror(), context.getMeta().java_lang_Class_name);

            context.getRegistries().recordConstraint(type, oldKlass, oldKlass.getDefiningClassLoader());
        }
        oldKlass.redefineClass(packet, refreshSubClasses, ids);
        return 0;
    }
}
