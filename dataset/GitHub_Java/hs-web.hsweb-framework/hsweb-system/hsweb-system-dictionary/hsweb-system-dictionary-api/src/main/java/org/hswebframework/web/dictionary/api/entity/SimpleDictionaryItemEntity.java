/*
 *  Copyright 2016 http://www.hswebframework.org
 *  
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 */
package org.hswebframework.web.dictionary.api.entity;

import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.commons.entity.SimpleTreeSortSupportEntity;

import java.util.List;

/**
 * 数据字典选项
 *
 * @author hsweb-generator-online
 */
@Getter
@Setter
public class SimpleDictionaryItemEntity extends SimpleTreeSortSupportEntity<String> implements DictionaryItemEntity {
    //字典id
    private String dictId;
    //名称
    private String name;
    //字典值
    private String value;
    //字典文本
    private String text;
    //字典值类型
    private String valueType;
    //是否启用
    private Byte status;
    //说明
    private String describe;
    //快速搜索码
    private String searchCode;

    // 使用表达式拼接text
    // #value+'('+#context.otherVal+')'
    private String textExpression;

    private String valueExpression;

    private Integer ordinal;

    private List<DictionaryItemEntity> children;


}