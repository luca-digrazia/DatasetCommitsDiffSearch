package com.oracle.truffle.espresso.debugger.api;

public interface FieldRef {

    byte getTagConstant();

    String getNameAsString();

    String getTypeAsString();

    String getGenericSignatureAsString();

    int getModifiers();

    klassRef getDeclaringKlass();

    Object getValue(Object self);

    void setValue(Object self, Object value);
}
