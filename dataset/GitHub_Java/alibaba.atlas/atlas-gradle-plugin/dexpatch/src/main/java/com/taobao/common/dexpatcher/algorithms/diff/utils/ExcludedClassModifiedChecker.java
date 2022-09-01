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

package com.taobao.common.dexpatcher.algorithms.diff.utils;

import com.taobao.common.dexpatcher.Configuration;
import com.taobao.dex.ClassDef;
import com.taobao.dex.Dex;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Created by tangyinsheng on 2016/4/14.
 */
public final class ExcludedClassModifiedChecker {
    private static final int STMCODE_START                                         = 0x00;
    private static final int STMCODE_ERROR_PRIMARY_OLD_DEX_IS_MISSING              = 0x01;
    private static final int STMCODE_ERROR_PRIMARY_NEW_DEX_IS_MISSING              = 0x02;
    private static final int STMCODE_ERROR_LOADER_CLASS_NOT_IN_PRIMARY_OLD_DEX     = 0x03;
    private static final int STMCODE_ERROR_LOADER_CLASS_IN_PRIMARY_DEX_MISMATCH    = 0x04;
    private static final int STMCODE_ERROR_LOADER_CLASS_FOUND_IN_SECONDARY_OLD_DEX = 0x05;
    private static final int STMCODE_ERROR_LOADER_CLASS_FOUND_IN_SECONDARY_NEW_DEX = 0x06;
    private static final int STMCODE_ERROR_LOADER_CLASS_CHANGED                    = 0x07;
    private static final int STMCODE_END                                           = 0x08;
    private final Configuration config;
    private final DexClassesComparator dexCmptor;
    private Dex oldDex = null;
    private Dex newDex = null;
    private List<DexClassesComparator.DexClassInfo> deletedClassInfos = null;
    private List<DexClassesComparator.DexClassInfo> addedClassInfos = null;
    private Map<String, DexClassesComparator.DexClassInfo[]> changedClassInfosMap = null;
    private Set<String> oldClassesDescToCheck = new HashSet<String>();
    private Set<String> newClassesDescToCheck = new HashSet<String>();

    public ExcludedClassModifiedChecker(Configuration config) {
        this.config = config;
        this.dexCmptor = new DexClassesComparator(config.mDexLoaderPattern);
    }

    public void checkIfExcludedClassWasModifiedInNewDex(File oldFile, File newFile) throws IOException, TinkerPatchException {
        if (oldFile == null && newFile == null) {
            throw new TinkerPatchException("both oldFile and newFile are null.");
        }

        oldDex = (oldFile != null ? new Dex(oldFile) : null);
        newDex = (newFile != null ? new Dex(newFile) : null);

        int stmCode = STMCODE_START;

        while (stmCode != STMCODE_END) {
            switch (stmCode) {
                /**
                 * Check rule:
                 * Loader classes must only appear in primary dex and each of them in primary old dex should keep
                 * completely consistent in new primary dex.
                 *
                 * An error is announced when any of these conditions below is fit:
                 * 1. Primary old dex is missing.
                 * 2. Primary new dex is missing.
                 * 3. There are not any loader classes in primary old dex.
                 * 4. There are some new loader classes added in new primary dex.
                 * 5. Loader classes in old primary dex are modified, deleted in new primary dex.
                 * 6. Loader classes are found in secondary old dexes.
                 * 7. Loader classes are found in secondary new dexes.
                 */
                case STMCODE_START: {
                    boolean isPrimaryDex = isPrimaryDex((oldFile == null ? newFile : oldFile));

                    if (isPrimaryDex) {
                        if (oldFile == null) {
                            stmCode = STMCODE_ERROR_PRIMARY_OLD_DEX_IS_MISSING;
                        } else if (newFile == null) {
                            stmCode = STMCODE_ERROR_PRIMARY_NEW_DEX_IS_MISSING;
                        } else {
                            dexCmptor.startCheck(oldDex, newDex);
                            deletedClassInfos = dexCmptor.getDeletedClassInfos();
                            addedClassInfos = dexCmptor.getAddedClassInfos();
                            changedClassInfosMap = dexCmptor.getChangedClassDescToInfosMap();

                            // All loader classes are in new dex, while none of them in old one.
                            if (deletedClassInfos.isEmpty() && changedClassInfosMap.isEmpty() && !addedClassInfos.isEmpty()) {
                                stmCode = STMCODE_ERROR_LOADER_CLASS_NOT_IN_PRIMARY_OLD_DEX;
                            } else {
                                if (deletedClassInfos.isEmpty() && addedClassInfos.isEmpty()) {
                                    // class descriptor is completely matches, see if any contents changes.
                                    if (changedClassInfosMap.isEmpty()) {
                                        stmCode = STMCODE_END;
                                    } else {
                                        stmCode = STMCODE_ERROR_LOADER_CLASS_CHANGED;
                                    }
                                } else {
                                    stmCode = STMCODE_ERROR_LOADER_CLASS_IN_PRIMARY_DEX_MISMATCH;
                                }
                            }
                        }
                    } else {
                        Set<Pattern> patternsOfClassDescToCheck = new HashSet<Pattern>();
                        for (String patternStr : config.mDexLoaderPattern) {
                            patternsOfClassDescToCheck.add(
                                Pattern.compile(
                                    PatternUtils.dotClassNamePatternToDescriptorRegEx(patternStr)
                                )
                            );
                        }

                        if (oldDex != null) {
                            oldClassesDescToCheck.clear();
                            for (ClassDef classDef : oldDex.classDefs()) {
                                String desc = oldDex.typeNames().get(classDef.typeIndex);
                                if (Utils.isStringMatchesPatterns(desc, patternsOfClassDescToCheck)) {
                                    oldClassesDescToCheck.add(desc);
                                }
                            }
                            if (!oldClassesDescToCheck.isEmpty()) {
                                stmCode = STMCODE_ERROR_LOADER_CLASS_FOUND_IN_SECONDARY_OLD_DEX;
                                break;
                            }
                        }

                        if (newDex != null) {
                            newClassesDescToCheck.clear();
                            for (ClassDef classDef : newDex.classDefs()) {
                                String desc = newDex.typeNames().get(classDef.typeIndex);
                                if (Utils.isStringMatchesPatterns(desc, patternsOfClassDescToCheck)) {
                                    newClassesDescToCheck.add(desc);
                                }
                            }
                            if (!newClassesDescToCheck.isEmpty()) {
                                stmCode = STMCODE_ERROR_LOADER_CLASS_FOUND_IN_SECONDARY_NEW_DEX;
                                break;
                            }
                        }

                        stmCode = STMCODE_END;
                    }
                    break;
                }
                case STMCODE_ERROR_PRIMARY_OLD_DEX_IS_MISSING: {
                    throw new TinkerPatchException("old primary dex is missing.");
                }
                case STMCODE_ERROR_PRIMARY_NEW_DEX_IS_MISSING: {
                    throw new TinkerPatchException("new primary dex is missing.");
                }
                case STMCODE_ERROR_LOADER_CLASS_NOT_IN_PRIMARY_OLD_DEX: {
                    throw new TinkerPatchException("all loader classes don't appear in old primary dex.");
                }
                case STMCODE_ERROR_LOADER_CLASS_IN_PRIMARY_DEX_MISMATCH: {
                    throw new TinkerPatchException(
                        "loader classes in old primary dex are mismatched to those in new primary dex, \n"
                            + "if deleted classes is not empty, check if your dex division strategy is fine. \n"
                            + "added classes: " + Utils.collectionToString(addedClassInfos) + "\n"
                            + "deleted classes: " + Utils.collectionToString(deletedClassInfos)
                    );
                }
                case STMCODE_ERROR_LOADER_CLASS_FOUND_IN_SECONDARY_OLD_DEX: {
                    throw new TinkerPatchException("loader classes are found in old secondary dex. Found classes: " + Utils.collectionToString(oldClassesDescToCheck));
                }
                case STMCODE_ERROR_LOADER_CLASS_FOUND_IN_SECONDARY_NEW_DEX: {
                    throw new TinkerPatchException("loader classes are found in new secondary dex. Found classes: " + Utils.collectionToString(newClassesDescToCheck));
                }
                case STMCODE_ERROR_LOADER_CLASS_CHANGED: {
                    String msg =
                        "some loader class has been changed in new dex."
                            + " Such these changes will not take effect!!"
                            + " related classes: "
                            + Utils.collectionToString(changedClassInfosMap.keySet());
                    throw new TinkerPatchException(msg);
                }
                default: {
//                    Logger.e("internal-error: unexpected stmCode.");
                    stmCode = STMCODE_END;
                    break;
                }
            }
        }
    }

    public boolean isPrimaryDex(File dexFile) {
       return true;
    }
}
