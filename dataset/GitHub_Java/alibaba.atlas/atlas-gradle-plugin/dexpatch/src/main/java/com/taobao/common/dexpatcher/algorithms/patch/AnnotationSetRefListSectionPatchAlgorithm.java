/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.taobao.common.dexpatcher.algorithms.patch;

import com.taobao.common.dexpatcher.struct.DexPatchFile;
import com.taobao.common.dexpatcher.struct.SmallPatchedDexItemFile;
import com.taobao.dex.AnnotationSetRefList;
import com.taobao.dex.Dex;
import com.taobao.dex.TableOfContents;
import com.taobao.dex.io.DexDataBuffer;
import com.taobao.dx.util.IndexMap;

/**
 * Created by tangyinsheng on 2016/7/4.
 */
public class AnnotationSetRefListSectionPatchAlgorithm extends DexSectionPatchAlgorithm<AnnotationSetRefList> {
    private TableOfContents.Section patchedAnnotationSetRefListTocSec = null;
    private Dex.Section patchedAnnotationSetRefListSec = null;

    public AnnotationSetRefListSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            Dex patchedDex,
            IndexMap oldToFullPatchedIndexMap,
            IndexMap fullPatchedToSmallPatchedIndexMap,
            final SmallPatchedDexItemFile extraInfoFile
    ) {
        this(
                patchFile,
                oldDex,
                patchedDex,
                oldToFullPatchedIndexMap,
                fullPatchedToSmallPatchedIndexMap,
                new SmallPatchedDexItemChooser() {
                    @Override
                    public boolean isPatchedItemInSmallPatchedDex(
                            String oldDexSign, int patchedItemIndex
                    ) {
                        return extraInfoFile.isAnnotationSetRefListInSmallPatchedDex(
                                oldDexSign, patchedItemIndex
                        );
                    }
                }
        );
    }

    public AnnotationSetRefListSectionPatchAlgorithm(
            DexPatchFile patchFile,
            Dex oldDex,
            Dex patchedDex,
            IndexMap oldToFullPatchedIndexMap,
            IndexMap fullPatchedToSmallPatchedIndexMap,
            SmallPatchedDexItemChooser spdItemChooser
    ) {
        super(
                patchFile,
                oldDex,
                oldToFullPatchedIndexMap,
                fullPatchedToSmallPatchedIndexMap,
                spdItemChooser
        );

        if (patchedDex != null) {
            this.patchedAnnotationSetRefListTocSec
                    = patchedDex.getTableOfContents().annotationSetRefLists;
            this.patchedAnnotationSetRefListSec
                    = patchedDex.openSection(this.patchedAnnotationSetRefListTocSec);
        }
    }

    @Override
    protected TableOfContents.Section getTocSection(Dex dex) {
        return dex.getTableOfContents().annotationSetRefLists;
    }

    @Override
    protected AnnotationSetRefList nextItem(DexDataBuffer section) {
        return section.readAnnotationSetRefList();
    }

    @Override
    protected int getItemSize(AnnotationSetRefList item) {
        return item.byteCountInDex();
    }

    @Override
    protected int getFullPatchSectionBase() {
        if (this.patchFile != null) {
            return this.patchFile.getPatchedAnnotationSetRefListSectionOffset();
        } else {
            return getTocSection(this.oldDex).off;
        }
    }

    @Override
    protected AnnotationSetRefList adjustItem(IndexMap indexMap, AnnotationSetRefList item) {
        return indexMap.adjust(item);
    }

    @Override
    protected int writePatchedItem(AnnotationSetRefList patchedItem) {
        ++this.patchedAnnotationSetRefListTocSec.size;
        return this.patchedAnnotationSetRefListSec.writeAnnotationSetRefList(patchedItem);
    }

    @Override
    protected void updateIndexOrOffset(IndexMap indexMap, int oldIndex, int oldOffset, int newIndex, int newOffset) {
        if (oldOffset != newOffset) {
            indexMap.mapAnnotationSetRefListOffset(oldOffset, newOffset);
        }
    }

    @Override
    protected void markDeletedIndexOrOffset(IndexMap indexMap, int deletedIndex, int deletedOffset) {
        indexMap.markAnnotationSetRefListDeleted(deletedOffset);
    }
}
