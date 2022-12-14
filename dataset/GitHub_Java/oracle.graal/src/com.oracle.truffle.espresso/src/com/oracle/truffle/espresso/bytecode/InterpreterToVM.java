package com.oracle.truffle.espresso.bytecode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.FieldInfo;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.MethodInfo;
import com.oracle.truffle.espresso.intrinsics.EspressoIntrinsics;
import com.oracle.truffle.espresso.intrinsics.Intrinsic;
import com.oracle.truffle.espresso.intrinsics.Target_java_io_FileDescriptor;
import com.oracle.truffle.espresso.intrinsics.Target_java_io_FileInputStream;
import com.oracle.truffle.espresso.intrinsics.Target_java_io_FileOutputStream;
import com.oracle.truffle.espresso.intrinsics.Target_java_io_UnixFileSystem;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Class;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_ClassLoader;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Double;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Float;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Object;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Package;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Runtime;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_StrictMath;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_String;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_System;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Thread;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_Throwable;
import com.oracle.truffle.espresso.intrinsics.Target_java_lang_reflect_Array;
import com.oracle.truffle.espresso.intrinsics.Target_java_security_AccessController;
import com.oracle.truffle.espresso.intrinsics.Target_java_util_concurrent_atomic_AtomicLong;
import com.oracle.truffle.espresso.intrinsics.Target_java_util_jar_JarFile;
import com.oracle.truffle.espresso.intrinsics.Target_java_util_zip_ZipFile;
import com.oracle.truffle.espresso.intrinsics.Target_sun_launcher_LauncherHelper;
import com.oracle.truffle.espresso.intrinsics.Target_sun_misc_Perf;
import com.oracle.truffle.espresso.intrinsics.Target_sun_misc_Signal;
import com.oracle.truffle.espresso.intrinsics.Target_sun_misc_URLClassPath;
import com.oracle.truffle.espresso.intrinsics.Target_sun_misc_Unsafe;
import com.oracle.truffle.espresso.intrinsics.Target_sun_misc_VM;
import com.oracle.truffle.espresso.intrinsics.Target_sun_reflect_NativeConstructorAccessorImpl;
import com.oracle.truffle.espresso.intrinsics.Target_sun_reflect_Reflection;
import com.oracle.truffle.espresso.intrinsics.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.meta.MetaUtil;
import com.oracle.truffle.espresso.nodes.IntrinsicRootNode;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.StaticObjectArray;
import com.oracle.truffle.espresso.runtime.StaticObjectImpl;

import static com.oracle.truffle.espresso.meta.Meta.meta;

public class    InterpreterToVM {

    private final Map<MethodKey, CallTarget> intrinsics = new HashMap<>();

    public static List<Class<?>> DEFAULTS = Arrays.asList(
                    Target_java_io_FileDescriptor.class,
                    Target_java_io_FileInputStream.class,
                    Target_java_io_FileOutputStream.class,
                    Target_java_io_UnixFileSystem.class,
                    Target_java_lang_Class.class,
                    Target_java_lang_ClassLoader.class,
                    Target_java_lang_Double.class,
                    Target_java_lang_Float.class,
                    Target_java_lang_StrictMath.class,
                    Target_java_lang_Package.class,
                    Target_java_lang_Object.class,
                    Target_java_lang_Runtime.class,
                    Target_java_lang_String.class,
                    Target_java_lang_System.class,
                    Target_java_lang_Thread.class,
                    Target_java_lang_Throwable.class,
                    Target_java_lang_reflect_Array.class,
                    Target_java_security_AccessController.class,
                    Target_java_util_concurrent_atomic_AtomicLong.class,
                    Target_java_util_jar_JarFile.class,
                    Target_java_util_zip_ZipFile.class,
                    Target_sun_launcher_LauncherHelper.class,
                    Target_sun_misc_Perf.class,
                    Target_sun_misc_Signal.class,
                    Target_sun_misc_Unsafe.class,
                    Target_sun_misc_URLClassPath.class,
                    Target_sun_misc_VM.class,
                    Target_sun_reflect_NativeConstructorAccessorImpl.class,
                    Target_sun_reflect_Reflection.class);

    private InterpreterToVM(EspressoLanguage language, List<Class<?>> intrinsics) {
        for (Class<?> clazz : intrinsics) {
            registerIntrinsics(clazz, language);
        }
    }

    private InterpreterToVM(EspressoLanguage language, Class<?>... intrinsics) {
        this(language, Arrays.asList(intrinsics));
    }

    public InterpreterToVM(EspressoLanguage language) {
        this(language, DEFAULTS);
    }

    public StaticObject intern(StaticObject obj) {
        assert obj.getKlass().getTypeDescriptor().equals(obj.getKlass().getContext().getTypeDescriptors().STRING);
        return obj.getKlass().getContext().getStrings().intern(obj);
    }

    private static MethodKey getMethodKey(MethodInfo method) {
        return new MethodKey(
                        method.getDeclaringClass().getName(),
                        method.getName(),
                        method.getSignature().toString());
    }

    @CompilerDirectives.TruffleBoundary
    public CallTarget getIntrinsic(MethodInfo method) {
        assert method != null;
        return intrinsics.get(getMethodKey(method));
    }

    private static final class MethodKey {
        private final String clazz;
        private final String methodName;
        private final String signature;

        public MethodKey(String clazz, String methodName, String signature) {
            this.clazz = clazz;
            this.methodName = methodName;
            this.signature = signature;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            MethodKey methodKey = (MethodKey) o;
            return Objects.equals(clazz, methodKey.clazz) &&
                            Objects.equals(methodName, methodKey.methodName) &&
                            Objects.equals(signature, methodKey.signature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz, methodName, signature);
        }

        @Override
        public String toString() {
            return "MethodKey{" +
                            "clazz='" + clazz + '\'' +
                            ", methodName='" + methodName + '\'' +
                            ", signature='" + signature + '\'' +
                            '}';
        }
    }

    public static String fixTypeName(String type) {
        if ((type.startsWith("L") && type.endsWith(";"))) {
            return type;
        }

        if (type.startsWith("[")) {
            return type.replace('.', '/');
        }

        if (type.endsWith("[]")) {
            return "[" + fixTypeName(type.substring(0, type.length() - 2));
        }

        switch (type) {
            case "boolean":
                return "Z";
            case "byte":
                return "B";
            case "char":
                return "C";
            case "double":
                return "D";
            case "float":
                return "F";
            case "int":
                return "I";
            case "long":
                return "J";
            case "short":
                return "S";
            case "void":
                return "V";
            default:
                return "L" + type.replace('.', '/') + ";";
        }
    }

    public void registerIntrinsics(Class<?> clazz, EspressoLanguage language) {

        String className;
        Class<?> annotatedClass = clazz.getAnnotation(EspressoIntrinsics.class).value();
        if (annotatedClass == EspressoIntrinsics.class) {
            // Target class is derived from class name by simple substitution
            // e.g. Target_java_lang_System becomes java.lang.System
            assert clazz.getSimpleName().startsWith("Target_");
            className = MetaUtil.toInternalName(clazz.getSimpleName().substring("Target_".length()).replace('_', '.'));
        } else {
            className = MetaUtil.toInternalName(annotatedClass.getName());
        }

        for (Method method : clazz.getDeclaredMethods()) {
            Intrinsic intrinsic = method.getAnnotation(Intrinsic.class);
            if (intrinsic == null) {
                continue;
            }

            MethodHandle handle;
            try {
                handle = MethodHandles.publicLookup().unreflect(method);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            RootCallTarget callTarget = Truffle.getRuntime().createCallTarget(new IntrinsicRootNode(language, handle));
            StringBuilder signature = new StringBuilder("(");
            Parameter[] parameters = method.getParameters();
            for (int i = intrinsic.hasReceiver() ? 1 : 0; i < parameters.length; i++) {
                Parameter parameter = parameters[i];
                String parameterTypeName;
                Type annotatedType = parameter.getAnnotatedType().getAnnotation(Type.class);
                if (annotatedType != null) {
                    parameterTypeName = annotatedType.value().getName();
                } else {
                    parameterTypeName = parameter.getType().getName();
                }
                signature.append(fixTypeName(parameterTypeName));
            }
            signature.append(')');

            Type annotatedReturnType = method.getAnnotatedReturnType().getAnnotation(Type.class);
            String returnTypeName;
            if (annotatedReturnType != null) {
                returnTypeName = annotatedReturnType.value().getName();
            } else {
                returnTypeName = method.getReturnType().getName();
            }
            signature.append(fixTypeName(returnTypeName));

            String methodName = intrinsic.methodName();
            if (methodName.length() == 0) {
                methodName = method.getName();
            }

            registerIntrinsic(fixTypeName(className), methodName, signature.toString(), callTarget);
        }
    }

    public void registerIntrinsic(String clazz, String methodName, String signature, CallTarget intrinsic) {

        MethodKey key = new MethodKey(clazz, methodName, signature);
        assert !intrinsics.containsKey(key) : key + " intrinsic is already registered";
        assert intrinsic != null;
        intrinsics.put(key, intrinsic);
    }

    // region Get (array) operations

    public int getArrayInt(int index, Object arr) {
        return ((int[]) arr)[index];
    }

    public Object getArrayObject(int index, Object arr) {
        return ((StaticObjectArray) arr).getWrapped()[index];
    }

    public long getArrayLong(int index, Object arr) {
        return ((long[]) arr)[index];
    }

    public float getArrayFloat(int index, Object arr) {
        return ((float[]) arr)[index];
    }

    public double getArrayDouble(int index, Object arr) {
        return ((double[]) arr)[index];
    }

    public byte getArrayByte(int index, Object arr) {
        if (arr instanceof boolean[]) {
            return (byte) (((boolean[]) arr)[index] ? 1 : 0);
        }
        return ((byte[]) arr)[index];
    }

    public char getArrayChar(int index, Object arr) {
        return ((char[]) arr)[index];
    }

    public short getArrayShort(int index, Object arr) {
        return ((short[]) arr)[index];
    }
    // endregion

    // region Set (array) operations
    public void setArrayInt(int value, int index, Object arr) {
        ((int[]) arr)[index] = value;
    }

    public void setArrayLong(long value, int index, Object arr) {
        ((long[]) arr)[index] = value;
    }

    public void setArrayFloat(float value, int index, Object arr) {
        ((float[]) arr)[index] = value;
    }

    public void setArrayDouble(double value, int index, Object arr) {
        ((double[]) arr)[index] = value;
    }

    public void setArrayByte(byte value, int index, Object arr) {
        if (arr instanceof boolean[]) {
            assert value == 0 || value == 1;
            ((boolean[]) arr)[index] = (value != 0);
        } else {
            ((byte[]) arr)[index] = value;
        }
    }

    public void setArrayChar(char value, int index, Object arr) {
        ((char[]) arr)[index] = value;
    }

    public void setArrayShort(short value, int index, Object arr) {
        ((short[]) arr)[index] = value;
    }

    public void setArrayObject(Object value, int index, Object arr) {
        // TODO(peterssen): Array store check.
        ((StaticObjectArray) arr).getWrapped()[index] = value;
    }
    // endregion

    // region Monitor enter/exit
    public void monitorEnter(Object obj) {
        // TODO(peterssen): Nop for single-threaded language.
        // UNSAFE.monitorEnter(obj);
    }

    public void monitorExit(Object obj) {
        // TODO(peterssen): Nop for single-threaded language.
        // UNSAFE.monitorExit(obj);
    }
    // endregion

    public boolean getFieldBoolean(StaticObject obj, FieldInfo field) {
        return (boolean) ((StaticObjectImpl) obj).getField(field);
    }

    public int getFieldInt(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Int;
        return (int) ((StaticObjectImpl) obj).getField(field);
    }

    public long getFieldLong(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Long;
        return (long) ((StaticObjectImpl) obj).getField(field);
    }

    public byte getFieldByte(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Byte;
        return (byte) ((StaticObjectImpl) obj).getField(field);
    }

    public short getFieldShort(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Short;
        return (short) ((StaticObjectImpl) obj).getField(field);
    }

    public float getFieldFloat(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Float;
        return (float) ((StaticObjectImpl) obj).getField(field);
    }

    public double getFieldDouble(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Double;
        return (double) ((StaticObjectImpl) obj).getField(field);
    }

    public Object getFieldObject(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Object;
        return ((StaticObjectImpl) obj).getField(field);
    }

    public char getFieldChar(StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Char;
        return (char) ((StaticObjectImpl) obj).getField(field);
    }

    public void setFieldBoolean(boolean value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Boolean;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldByte(byte value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Byte;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldChar(char value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Char;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldShort(short value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Short;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldInt(int value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Int;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldLong(long value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Long;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldFloat(float value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Float;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldDouble(double value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Double;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public void setFieldObject(Object value, StaticObject obj, FieldInfo field) {
        assert field.getKind() == JavaKind.Object;
        ((StaticObjectImpl) obj).setField(field, value);
    }

    public StaticObject newArray(Klass componentType, int length) {
        assert !componentType.isPrimitive();
        Object[] arr = new Object[length];
        Arrays.fill(arr, StaticObject.NULL);
        return new StaticObjectArray(componentType, arr);
    }

    public StaticObject newMultiArray(Klass componentType, int[] dimensions) {
        // assert !componentType.isPrimitive();
        throw EspressoError.unimplemented();
    }

    public boolean instanceOf(Object instance, Klass typeToCheck) {
        assert instance != null : "use StaticObject.NULL";
        if (instance == StaticObject.NULL) {
            return false;
        }

        // WIP
//        Meta meta = typeToCheck.getContext().getMeta();
//
//        if (meta(typeToCheck).isArray()) {
//            if (meta.meta(instance).isArray()) {
//                if (!typeToCheck.getComponentType().isPrimitive() && !meta.meta(instance).rawKlass().getComponentType().isPrimitive()) {
//
//                }
//            }
//        }

        if (instance instanceof StaticObject) {

            if (typeToCheck.isInterface()) {
                Klass k = ((StaticObject) instance).getKlass();
                while (k != null) {
                    if (Arrays.asList(k.getInterfaces()).contains(typeToCheck)) {
                        return true;
                    }
                    k = k.getSuperclass();
                }
            }

            for (Klass k = ((StaticObject) instance).getKlass(); k != null; k = k.getSuperclass()) {
                if (k == typeToCheck) {
                    return true;
                }
            }
        } else {
            assert instance.getClass().isArray();
            // TODO(peterssen): Handle T[] instanceof C.
            throw EspressoError.unimplemented();
        }

        return false;
    }

    public Object checkCast(Object instance, Klass klass) {
        if (instance == StaticObject.NULL || instanceOf(instance, klass)) {
            return instance;
        }
        // TODO(peterssen): Throw guest exception.
        // throw newClassCastException();
        throw new ClassCastException();
    }

    public StaticObject newObject(Klass klass) {
        klass.initialize();
        assert klass != null && !klass.isArray();
        return new StaticObjectImpl(klass);
    }

    public int arrayLength(Object arr) {
        if (arr instanceof StaticObjectArray) {
            return ((StaticObjectArray) arr).getWrapped().length;
        } else {
            assert arr.getClass().isArray();
            // Primitive arrays are shared in the guest/host.
            return Array.getLength(arr);
        }
    }

}
