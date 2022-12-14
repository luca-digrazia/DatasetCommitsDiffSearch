package com.oracle.truffle.wasm.collection;

public class ByteList {
    private byte[] array;
    private int offset;

    public ByteList() {
        this.array = null;
        this.offset = 0;
    }

    public void add(byte b) {
        ensureSize();
        array[offset] = b;
        offset++;
    }

    private void ensureSize() {
        if (array == null) {
            array = new byte[4];
        } else if (offset == array.length) {
            byte[] narray = new byte[array.length * 2];
            System.arraycopy(array, 0, narray, 0, offset);
            array = narray;
        }
    }

    public byte[] toArray() {
        byte[] result = new byte[offset];
        System.arraycopy(array, 0, result, 0, offset);
        return result;
    }
}
