/*
 *
 *  * Copyright 2016 http://www.hswebframework.org
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package org.hswebframework.web.bean.config;


import org.hswebframework.web.commons.beans.CloneableBean;
import org.hswebframework.web.commons.beans.SimpleGenericBean;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO 完成注释
 *
 * @author zhouhao
 */
public class SimpleConfigBean extends SimpleGenericBean<String> implements ConfigBean {

    //备注
    private String remark;

    //配置内容
    private List<ConfigContent> content;

    //创建日期
    @NotNull
    private java.util.Date createDate;

    //最后一次修改日期
    private java.util.Date updateDate;

    //配置分类ID
    private String classifiedId;

    @NotNull
    private String creatorId;

    private Map<String, ConfigContent> cache;

    @Override
    public String getCreatorId() {
        return creatorId;
    }

    @Override
    public void setCreatorId(String creatorId) {
        this.creatorId = creatorId;
    }

    @Override
    public String getRemark() {
        return remark;
    }

    @Override
    public void setRemark(String remark) {
        this.remark = remark;
    }

    @Override
    public List<ConfigContent> getContent() {
        return content;
    }

    @Override
    public ConfigBean addContent(String key, Object value, String comment) {
        if (content == null) content = new ArrayList<>();
        content.add(new org.hswebframework.web.bean.config.ConfigContent(key, value, comment));
        return this;
    }

    @Override
    public ConfigContent get(String key) {
        if (cache == null) {
            synchronized (this) {
                if (cache == null) {
                    if (content == null || content.isEmpty()) {
                        return null;
                    }
                    cache = content.stream()
                            .collect(Collectors.toMap(ConfigContent::getKey, content -> content));
                }
            }
        }
        return cache.get(key);
    }

    @Override
    public void setContent(List<ConfigContent> content) {
        this.content = content;
    }

    @Override
    public Date getCreateDate() {
        return createDate;
    }

    @Override
    public void setCreateDate(Date createDate) {
        this.createDate = createDate;
    }

    @Override
    public Date getUpdateDate() {
        return updateDate;
    }

    @Override
    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

    @Override
    public String getClassifiedId() {
        return classifiedId;
    }

    @Override
    public void setClassifiedId(String classifiedId) {
        this.classifiedId = classifiedId;
    }

    @Override
    public SimpleConfigBean clone() {
        SimpleConfigBean cloned = new SimpleConfigBean();
        cloned.setId(getId());
        cloned.setCreatorId(getCreatorId());
        cloned.setCreateDate(getCreateDate());
        cloned.content = getContent().stream().map(ConfigContent::clone).collect(Collectors.toList());
        return cloned;
    }
}
