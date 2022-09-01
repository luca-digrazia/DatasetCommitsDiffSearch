/*
 * Copyright 2016 http://www.hswebframework.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.hswebframework.web.controller;

import org.hswebframework.web.authorization.Permission;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.commons.entity.GenericEntity;
import org.hswebframework.web.controller.message.ResponseMessage;
import org.hswebframework.web.logging.AccessLogger;
import org.hswebframework.web.service.UpdateService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 通用实体更新控制器
 *
 * @author zhouhao
 * @see UpdateService
 * @since 3.0
 */
public interface GenericEntityUpdateController<E extends GenericEntity<PK>, PK> extends UpdateController<E, PK> {

    UpdateService<E> getService();

    @Authorize(action = Permission.ACTION_UPDATE)
    @PutMapping(path = "/{id}")
    @AccessLogger("{update_by_primary_key}")
    default ResponseMessage updateByPrimaryKey(@PathVariable PK id, @RequestBody E data) {
        data.setId(id);
        return ResponseMessage.ok(getService().updateByPk(data));
    }
}
