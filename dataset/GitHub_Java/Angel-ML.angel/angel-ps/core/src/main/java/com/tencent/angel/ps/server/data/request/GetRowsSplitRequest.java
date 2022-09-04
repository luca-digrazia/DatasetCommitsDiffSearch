/*
 * Tencent is pleased to support the open source community by making Angel available.
 *
 * Copyright (C) 2017-2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/Apache-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */


package com.tencent.angel.ps.server.data.request;

import com.tencent.angel.common.ByteBufSerdeUtils;
import io.netty.buffer.ByteBuf;

/**
 * Get a batch row splits rpc request.
 */
public class GetRowsSplitRequest extends RequestData {
  /**
   * row indexes
   */
  private int[] rowIds;

  /**
   * Create a new GetRowsSplitRequest.
   *
   * @param rowIds    row ids
   */
  public GetRowsSplitRequest(int[] rowIds) {
    this.rowIds = rowIds;
  }

  /**
   * Create a new GetRowsSplitRequest.
   */
  public GetRowsSplitRequest() {
    this(null);
  }

  public int[] getRowIds() {
    return rowIds;
  }

  @Override public void serialize(ByteBuf buf) {
    ByteBufSerdeUtils.serializeInts(buf, rowIds);
  }

  @Override public void deserialize(ByteBuf buf) {
    int readerIndex = buf.readerIndex();
    rowIds = ByteBufSerdeUtils.deserializeInts(buf);
    requestSize = buf.readerIndex() - readerIndex;
  }

  @Override public int bufferLen() {
    return ByteBufSerdeUtils.serializedIntsLen(rowIds);
  }
}