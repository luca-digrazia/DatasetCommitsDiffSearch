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
package com.tencent.angel.graph.client.node2vec.getfuncs.pullpathtail;

import com.tencent.angel.graph.client.node2vec.utils.SerDe;
import com.tencent.angel.ml.matrix.psf.get.base.PartitionGetResult;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class PullPathTailPartitionResult extends PartitionGetResult {
  private Long2ObjectOpenHashMap<long[]> partResult;

  public PullPathTailPartitionResult(Long2ObjectOpenHashMap<long[]> partResult) {
    this.partResult = partResult;
  }

  public PullPathTailPartitionResult() { super(); }

  public Long2ObjectOpenHashMap<long[]> getPartRestlt() {
    return partResult;
  }

  public void setPartRestlt(Long2ObjectOpenHashMap<long[]> partResult) {
    this.partResult = partResult;
  }

  @Override
  public void serialize(ByteBuf output) {
    SerDe.serLong2ArrayHashMap(partResult, output);
  }

  @Override
  public void deserialize(ByteBuf input) {
    partResult = SerDe.deserLong2LongArray(input);
  }

  @Override
  public int bufferLen() {
    return SerDe.getLong2ArrayHashMapSerSize(partResult);
  }
}
