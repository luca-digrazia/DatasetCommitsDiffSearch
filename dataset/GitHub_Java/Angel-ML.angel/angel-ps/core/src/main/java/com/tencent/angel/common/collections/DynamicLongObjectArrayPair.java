package com.tencent.angel.common.collections;

import com.tencent.angel.ps.storage.vector.element.IElement;
import io.netty.buffer.ByteBuf;

public class DynamicLongObjectArrayPair extends DynamicArray {

  private DynamicLongArray dynamicKeys;
  private DynamicObjectArray dynamicValues;

  public DynamicLongObjectArrayPair(int size) {
    dynamicKeys = new DynamicLongArray(size);
    dynamicValues = new DynamicObjectArray(size);
  }

  public DynamicLongObjectArrayPair() {
  }

  public void add(long key, IElement value) {
    dynamicKeys.add(key);
    dynamicValues.add(value);
  }

  public long[] getKeys() {
    return dynamicKeys.getData();
  }

  public IElement[] getValues() {
    return dynamicValues.getData();
  }

  @Override
  public void serialize(ByteBuf out) {
    dynamicKeys.serialize(out);
    dynamicValues.serialize(out);
  }

  @Override
  public void deserialize(ByteBuf in) {
    dynamicKeys = new DynamicLongArray();
    dynamicKeys.deserialize(in);
    dynamicValues = new DynamicObjectArray();
    dynamicValues.deserialize(in);
  }

  @Override
  public int bufferLen() {
    return dynamicKeys.bufferLen() + dynamicValues.bufferLen();
  }

  @Override
  public int size() {
    if(dynamicKeys != null) {
      return dynamicKeys.size();
    } else {
      return 0;
    }
  }
}
