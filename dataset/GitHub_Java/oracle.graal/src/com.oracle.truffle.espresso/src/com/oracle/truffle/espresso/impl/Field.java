package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.espresso.classfile.Constants;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Types;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.ModifiersProvider;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * Represents a resolved Espresso field.
 */
public final class Field implements ModifiersProvider {

    public static final Field[] EMPTY_ARRAY = new Field[0];

    private final LinkedField linkedField;
    private final ObjectKlass holder;
    private final Symbol<Type> type;
    private final Symbol<Name> name;
    private volatile Klass typeKlassCache;

    public Symbol<Type> getType() {
        return type;
    }

    public Field(LinkedField linkedField, ObjectKlass holder) {
        this.linkedField = linkedField;
        this.holder = holder;
        this.type = linkedField.getType();
        this.name = linkedField.getName();
    }

    public JavaKind getKind() {
        return Types.getJavaKind(getType());
    }

    public int getModifiers() {
        return linkedField.getFlags() & Constants.JVM_RECOGNIZED_FIELD_MODIFIERS;
    }

    public ObjectKlass getDeclaringKlass() {
        return holder;
    }

    public int getSlot() {
        return linkedField.getSlot();
    }

    public boolean isInternal() {
        // No internal fields in Espresso (yet).
        return false;
    }

    @Override
    public String toString() {
        return "EspressoField<" + getDeclaringKlass() + "." + getName() + " -> " + getType() + ">";
    }

    public Object get(StaticObject self) {
        assert getDeclaringKlass().isAssignableFrom(self.getKlass());
        InterpreterToVM vm = getDeclaringKlass().getContext().getInterpreterToVM();
        // @formatter:off
        // Checkstyle: stop
        switch (getKind()) {
            case Boolean : return vm.getFieldBoolean(self, this);
            case Byte    : return vm.getFieldByte(self, this);
            case Short   : return vm.getFieldShort(self, this);
            case Char    : return vm.getFieldChar(self, this);
            case Int     : return vm.getFieldInt(self, this);
            case Float   : return vm.getFieldFloat(self, this);
            case Long    : return vm.getFieldLong(self, this);
            case Double  : return vm.getFieldDouble(self, this);
            case Object  : return vm.getFieldObject(self, this);
            default      : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
    }

    public void set(StaticObject self, Object value) {
        assert value != null;
        assert getDeclaringKlass().isAssignableFrom(self.getKlass());
        InterpreterToVM vm = getDeclaringKlass().getContext().getInterpreterToVM();
        // @formatter:off
        // Checkstyle: stop
        switch (getKind()) {
            case Boolean : vm.setFieldBoolean((boolean) value, self, this); break;
            case Byte    : vm.setFieldByte((byte) value, self, this);       break;
            case Short   : vm.setFieldShort((short) value, self, this);     break;
            case Char    : vm.setFieldChar((char) value, self, this);       break;
            case Int     : vm.setFieldInt((int) value, self, this);         break;
            case Float   : vm.setFieldFloat((float) value, self, this);     break;
            case Long    : vm.setFieldLong((long) value, self, this);       break;
            case Double  : vm.setFieldDouble((double) value, self, this);   break;
            case Object  : vm.setFieldObject((StaticObject) value, self, this); break;
            default      : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
    }

    public Symbol<Name> getName() {
        return name;
    }

    public final Klass resolveTypeKlass() {
        Klass tk = typeKlassCache;
        if (tk == null) {
            synchronized (this) {
                tk = typeKlassCache;
                if (tk == null) {
                    tk = getDeclaringKlass().getRegistries().loadKlass(getType(), getDeclaringKlass().getDefiningClassLoader());
                    //tk = // holder.getConstantPool().resolvedKlassAt(linkedField.getParserField().getTypeIndex());
                    typeKlassCache = tk;
                }
            }
        }
        return typeKlassCache;
    }

    public Attribute getAttribute(Symbol<Name> name) {
        return linkedField.getAttribute(name);
    }
}
