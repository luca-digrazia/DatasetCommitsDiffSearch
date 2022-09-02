/*******************************************************************************
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
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
 *******************************************************************************/

package com.oracle.truffle.espresso.classfile;

import static com.oracle.truffle.espresso.classfile.Constants.ACC_ABSTRACT;
import static com.oracle.truffle.espresso.classfile.Constants.ACC_INTERFACE;
import static com.oracle.truffle.espresso.classfile.Constants.JVM_RECOGNIZED_CLASS_MODIFIERS;

import java.util.Objects;

import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ParserField;
import com.oracle.truffle.espresso.impl.ParserKlass;
import com.oracle.truffle.espresso.impl.ParserMethod;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.runtime.ClasspathFile;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.object.DebugCounter;

public final class ClassfileParser {

    public static final int MAGIC = 0xCAFEBABE;

    public static final int JAVA_4_VERSION = 48;
    public static final int JAVA_5_VERSION = 49;
    public static final int JAVA_6_VERSION = 50;
    public static final int JAVA_7_VERSION = 51;
    public static final int JAVA_8_VERSION = 52;
    public static final int JAVA_9_VERSION = 53;
    public static final int JAVA_10_VERSION = 54;
    public static final int JAVA_11_VERSION = 55;

    private static final int MAJOR_VERSION_JAVA_MIN = 0;
    private static final int MAJOR_VERSION_JAVA_MAX = JAVA_11_VERSION;

    public static final int STRICTER_ACCESS_CTRL_CHECK_VERSION = JAVA_5_VERSION;
    public static final int STACKMAP_ATTRIBUTE_MAJOR_VERSION = JAVA_6_VERSION;
    public static final int INVOKEDYNAMIC_MAJOR_VERSION = JAVA_7_VERSION;
    public static final int NO_RELAX_ACCESS_CTRL_CHECK_VERSION = JAVA_8_VERSION;
    public static final int DYNAMICCONSTANT_MAJOR_VERSION = JAVA_11_VERSION;

    private final ClasspathFile classfile;

    private final String requestedClassName;

    private final EspressoContext context;

    private final ClassfileStream stream;

    private String className;
    private int minorVersion;
    private int majorVersion;

    private int maxBootstrapMethodAttrIndex;
    private Tag badConstantSeen;

    private ConstantPool pool;

// /**
// * The host class for an anonymous class.
// */
// private final Klass hostClass;

    private Symbol<Type> typeDescriptor;

    private ClassfileParser(ClasspathFile classpathFile, String requestedClassName, Klass hostClass, EspressoContext context) {
        this.requestedClassName = requestedClassName;
        this.className = requestedClassName;
        // this.hostClass = hostClass;
        this.context = context;
        this.classfile = classpathFile;
        this.stream = new ClassfileStream(classfile);
    }

    private ClassfileParser(ClassfileStream stream, String requestedClassName, Klass hostClass, EspressoContext context) {
        this.requestedClassName = requestedClassName;
        this.className = requestedClassName;
        // this.hostClass = hostClass;
        this.context = context;
        this.classfile = null;
        this.stream = Objects.requireNonNull(stream);
    }

    void handleBadConstant(Tag tag, ClassfileStream s) {
        if (tag == Tag.MODULE || tag == Tag.PACKAGE) {
            if (majorVersion >= JAVA_9_VERSION) {
                s.readU2();
                badConstantSeen = tag;
                return;
            }
        }
        throw stream.classFormatError("Unknown constant tag %d", tag.getValue());
    }

    void updateMaxBootstrapMethodAttrIndex(int bsmAttrIndex) {
        if (maxBootstrapMethodAttrIndex < bsmAttrIndex) {
            maxBootstrapMethodAttrIndex = bsmAttrIndex;
        }
    }

    void checkInvokeDynamicSupport(Tag tag) {
        if (majorVersion < ClassfileParser.INVOKEDYNAMIC_MAJOR_VERSION) {
            stream.classFormatError("Class file version does not support constant tag %d", tag);
        }
    }

    void checkDynamicConstantSupport(Tag tag) {
        if (majorVersion < ClassfileParser.DYNAMICCONSTANT_MAJOR_VERSION) {
            stream.classFormatError("Class file version does not support constant tag %d", tag);
        }
    }

    public static ParserKlass parse(ClassfileStream stream, String requestedClassName, Klass hostClass, EspressoContext context) {
        return new ClassfileParser(stream, requestedClassName, hostClass, context).parseClass();
    }

    public static ParserKlass parse(ClasspathFile classpathFile, String requestedClassName, Klass hostClass, EspressoContext context) {
        return parse(new ClassfileStream(classpathFile), requestedClassName, hostClass, context);
    }

    public ParserKlass parseClass() {
        // magic
        int magic = stream.readS4();
        if (magic != MAGIC) {
            throw stream.classFormatError("Incompatible magic value %x in class file %s", magic, classfile);
        }

        minorVersion = stream.readU2();
        majorVersion = stream.readU2();
        if (majorVersion < MAJOR_VERSION_JAVA_MIN || majorVersion > MAJOR_VERSION_JAVA_MAX) {
            throw new UnsupportedClassVersionError("Unsupported class file version: " + majorVersion + "." + minorVersion);
        }

        this.pool = ConstantPool.parse(context.getLanguage(), stream, this);

        // JVM_ACC_MODULE is defined in JDK-9 and later.
        int accessFlags;
        if (majorVersion >= JAVA_9_VERSION) {
            accessFlags = stream.readU2() & (JVM_RECOGNIZED_CLASS_MODIFIERS | Constants.ACC_MODULE);
        } else {
            accessFlags = stream.readU2() & (JVM_RECOGNIZED_CLASS_MODIFIERS);
        }

        if ((accessFlags & ACC_INTERFACE) != 0 && majorVersion < JAVA_6_VERSION) {
            // Set abstract bit for old class files for backward compatibility
            accessFlags |= ACC_ABSTRACT;
        }

        boolean isModule = (accessFlags & Constants.ACC_MODULE) != 0;
        if (isModule) {
            throw new NoClassDefFoundError(className + " is not a class because access_flag ACC_MODULE is set");
        }

        if (badConstantSeen != null) {
            // Do not throw CFE until after the access_flags are checked because if
            // ACC_MODULE is set in the access flags, then NCDFE must be thrown, not CFE.
            // https://bugs.openjdk.java.net/browse/JDK-8175383
            throw classfile.classFormatError("Unknown constant tag %s", badConstantSeen);
        }

        // This class and superclass
        int thisClassIndex = stream.readU2();

        // this.typeDescriptor = pool.classAt(thisClassIndex).getName(pool);

        // Update className which could be null previously
        // to reflect the name in the constant pool
        // className = TypeDescriptor.slashified(typeDescriptor.toJavaName());

        // Checks if name in class file matches requested name
        if (requestedClassName != null && !requestedClassName.equals(className)) {
            throw new NoClassDefFoundError(className + " (wrong name: " + requestedClassName + ")");
        }

        // if this is an anonymous class fix up its name if it's in the unnamed
        // package. Otherwise, throw IAE if it is in a different package than
        // its host class.
// if (hostClass != null) {
// fixAnonymousClassName();
// }



        Symbol<Name> thisKlassName = pool.classAt(thisClassIndex).getName(pool);
        Symbol<Type> thisKlassType = context.getTypes().fromName(thisKlassName);

        Symbol<Type> superKlass = parseSuperKlass();
        Symbol<Type>[] superInterfaces = parseInterfaces();

        ParserField[] fields = parseFields();
        ParserMethod[] methods = parseMethods();
        Attribute[] attributes = parseAttributes();

        return new ParserKlass(pool, accessFlags, thisKlassName, thisKlassType, superKlass, superInterfaces, methods, fields, attributes);
    }

    private ParserMethod[] parseMethods() {
        int methodCount = stream.readU2();
        if (methodCount == 0) {
            return ParserMethod.EMPTY_ARRAY;
        }
        ParserMethod[] methods = new ParserMethod[methodCount];
        for (int i = 0; i < methodCount; ++i) {
            methods[i] = parseMethod();
        }
        return methods;
    }

    private ParserMethod parseMethod() {
        int flags = stream.readU2();
        int nameIndex = stream.readU2();
        int signatureIndex = stream.readU2();
        Attribute[] methodAttributes = parseAttributes();
        return new ParserMethod(flags, nameIndex, signatureIndex, methodAttributes);
    }

    private Attribute[] parseAttributes() {
        int attributeCount = stream.readU2();
        if (attributeCount == 0) {
            return Attribute.EMPTY_ARRAY;
        }
        Attribute[] attrs = new Attribute[attributeCount];
        for (int i = 0; i < attributeCount; i++) {
            attrs[i] = parseAttribute();
        }
        return attrs;
    }

    private Attribute parseAttribute() {
        int nameIndex = stream.readU2();
        Symbol<Name> name = pool.utf8At(nameIndex);
        if (CodeAttribute.NAME.equals(name)) {
            return parseCodeAttribute(name);
        }
        if (EnclosingMethodAttribute.NAME.equals(name)) {
            return parseEnclosingMethodAttribute(name);
        }
        if (InnerClassesAttribute.NAME.equals(name)) {
            return parseInnerClasses(name);
        }
        if (ExceptionsAttribute.NAME.equals(name)) {
            return parseExceptions(name);
        }
        if (BootstrapMethodsAttribute.NAME.equals(name)) {
            return parseBootstrapMethods(name);
        }
        int length = stream.readS4();
        byte[] data = stream.readByteArray(length);
        return new Attribute(name, data);
    }

    private ExceptionsAttribute parseExceptions(Symbol<Name> name) {
        /* int length = */ stream.readS4();
        int entryCount = stream.readU2();
        int[] entries = new int[entryCount];
        for (int i = 0; i < entryCount; ++i) {
            int index = stream.readU2();
            entries[i] = index;
        }
        return new ExceptionsAttribute(name, entries);
    }

    private BootstrapMethodsAttribute parseBootstrapMethods(Symbol<Name> name) {
        /* int length = */ stream.readS4();
        int entryCount = stream.readU2();
        BootstrapMethodsAttribute.Entry[] entries = new BootstrapMethodsAttribute.Entry[entryCount];
        for (int i = 0; i < entryCount; ++i) {
            int bootstrapMethodRef = stream.readU2();
            int numBootstrapArguments = stream.readU2();
            char[] bootstrapArguments = new char[numBootstrapArguments];
            for (int j = 0; j < numBootstrapArguments; ++j) {
                bootstrapArguments[j] = (char) stream.readU2();
            }
            entries[i] = new BootstrapMethodsAttribute.Entry((char) bootstrapMethodRef, bootstrapArguments);
        }
        return new BootstrapMethodsAttribute(name, entries);
    }

    private InnerClassesAttribute parseInnerClasses(Symbol<Name> name) {
        /* int length = */ stream.readS4();
        int entryCount = stream.readU2();
        InnerClassesAttribute.Entry[] entries = new InnerClassesAttribute.Entry[entryCount];
        for (int i = 0; i < entryCount; ++i) {
            entries[i] = parseInnerClassEntry();
        }
        return new InnerClassesAttribute(name, entries);
    }

    private InnerClassesAttribute.Entry parseInnerClassEntry() {
        int innerClassIndex = stream.readU2();
        int outerClassIndex = stream.readU2();
        int innerNameIndex = stream.readU2();
        int innerClassAccessFlags = stream.readU2();
        return new InnerClassesAttribute.Entry(innerClassIndex, outerClassIndex, innerNameIndex, innerClassAccessFlags);
    }

    private EnclosingMethodAttribute parseEnclosingMethodAttribute(Symbol<Name> name) {
        /* int length = */ stream.readS4();
        int classIndex = stream.readU2();
        int methodIndex = stream.readU2();
        return new EnclosingMethodAttribute(name, classIndex, methodIndex);
    }

    private CodeAttribute parseCodeAttribute(Symbol<Name> name) {
        /* int length = */ stream.readS4();
        int maxStack = stream.readU2();
        int maxLocals = stream.readU2();
        int codeLength = stream.readS4();
        byte[] code = stream.readByteArray(codeLength);
        ExceptionHandler[] entries = parseExceptionHandlerEntries();
        Attribute[] codeAttributes = parseAttributes();
        return new CodeAttribute(name, maxStack, maxLocals, code, entries, codeAttributes);
    }

    private ExceptionHandler[] parseExceptionHandlerEntries() {
        int count = stream.readU2();
        ExceptionHandler[] entries = new ExceptionHandler[count];
        for (int i = 0; i < count; i++) {
            int startPc = stream.readU2();
            int endPc = stream.readU2();
            int handlerPc = stream.readU2();
            int catchTypeIndex = stream.readU2();
            Symbol<Type> catchType = null;
            if (catchTypeIndex != 0) {
                catchType = context.getTypes().fromName(pool.classAt(catchTypeIndex).getName(pool));
            }
            entries[i] = new ExceptionHandler(startPc, endPc, handlerPc, catchTypeIndex, catchType);
        }
        return entries;
    }

    private ParserField parseField() {
        int flags = stream.readU2();
        int nameIndex = stream.readU2();
        int typeIndex = stream.readU2();
        Attribute[] fieldAttributes = parseAttributes();
        return new ParserField(flags, pool.utf8At(nameIndex), pool.utf8At(typeIndex), typeIndex, fieldAttributes);
    }

    private ParserField[] parseFields() {
        int fieldCount = stream.readU2();
        if (fieldCount == 0) {
            return ParserField.EMPTY_ARRAY;
        }
        ParserField[] fields = new ParserField[fieldCount];
        for (int i = 0; i < fieldCount; i++) {
            fields[i] = parseField();
        }
        return fields;
    }

    /**
     * Parses the reference to the super class. Resolving and checking the super class is done after
     * parsing the current class file so that resolution is only attempted if there are no format
     * errors in the current class file.
     */
    private Symbol<Type> parseSuperKlass() {
        int index = stream.readU2();
        if (index == 0) {
            if (!className.equals("Ljava/lang/Object;")) {
                throw classfile.classFormatError("Invalid superclass index 0");
            }
            return null;
        }
        return context.getTypes().fromName(pool.classAt(index).getName(pool));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Symbol<Type>[] parseInterfaces() {
        int interfaceCount = stream.readU2();
        if (interfaceCount == 0) {
            return Symbol.emptyArray();
        }
        Symbol<Type>[] interfaces = new Symbol[interfaceCount];
        for (int i = 0; i < interfaceCount; i++) {
            int interfaceIndex = stream.readU2();
            interfaces[i] = context.getTypes().fromName(pool.classAt(interfaceIndex).getName(pool));
        }
        return interfaces;
    }

    private static String getPackageName(String fqn) {
        int slash = fqn.lastIndexOf('/');
        if (slash == -1) {
            int first = 0;
            while (fqn.charAt(first) == '[') {
                first++;
            }
            if (fqn.charAt(first) == 'L') {
                assert fqn.endsWith(";");
                first++;
            }
            int end = fqn.lastIndexOf('/');
            if (end != -1) {
                return fqn.substring(first, end);
            }
        }
        return null;
    }

// /**
// * If the host class and the anonymous class are in the same package then do nothing. If the
// * anonymous class is in the unnamed package then move it to its host's package. If the classes
// * are in different packages then throw an {@link IllegalArgumentException}.
// */
// private void fixAnonymousClassName() {
// int slash = this.typeDescriptor.toJavaName().lastIndexOf('/');
// String hostPackageName = getPackageName(hostClass.getName());
// if (slash == -1) {
// // For an anonymous class that is in the unnamed package, move it to its host class's
// // package by prepending its host class's package name to its class name.
// if (hostPackageName != null) {
// String newClassName = 'L' + hostPackageName + '/' + this.typeDescriptor.toJavaName() + ';';
// this.className = pool.getContext().getTypeDescriptors().make(newClassName).toJavaName();
// }
// } else {
// String packageName = getPackageName(this.className);
// if (!hostPackageName.equals(packageName)) {
// throw new IllegalArgumentException("Host class " + hostClass + " and anonymous class " +
// this.className + " are in different packages");
// }
// }
// }
}