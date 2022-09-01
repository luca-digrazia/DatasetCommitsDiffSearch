package com.oracle.truffle.espresso.jdwp.api;

public interface LocalRef {
    int getStartBCI();

    String getNameAsString();

    String getTypeAsString();

    int getEndBCI();

    int getSlot();
}
