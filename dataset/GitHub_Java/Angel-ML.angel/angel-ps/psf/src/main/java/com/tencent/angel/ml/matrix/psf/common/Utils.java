/*
 * Tencent is pleased to support the open source community by making Angel available.
 *
 * Copyright (C) 2017 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.tencent.angel.ml.matrix.psf.common;

import com.tencent.angel.PartitionKey;

import java.util.Arrays;

public class Utils {
  public static boolean withinPart(PartitionKey partKey, int[] rowIds) {
    int startRow = partKey.getStartRow();
    int endRow = partKey.getEndRow();

    boolean allInPart = true;
    boolean hasInPart = false;
    boolean hasOutPart = false;

    for (int i = 0; i < rowIds.length; i++) {
      if (rowIds[i] < startRow || rowIds[i] >= endRow) {
        allInPart = false;
        hasOutPart = true;
      } else {
        hasInPart = true;
      }
    }

    if (hasInPart && hasOutPart) {
      throw new RuntimeException("rowIds: " + Arrays.toString(rowIds) + " in different parts");
    }
    return allInPart;
  }
}
