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
 *
 */

package org.hswebframework.web.authorization.oauth2.controller;

import io.swagger.annotations.Api;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.commons.entity.param.QueryParamEntity;
import org.hswebframework.web.controller.GenericEntityController;
import org.hswebframework.web.entity.oauth2.client.OAuth2ServerConfigEntity;
import org.hswebframework.web.logging.AccessLogger;
import org.hswebframework.web.service.oauth2.client.OAuth2ServerConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OAuth2服务配置
 *
 * @author hsweb-generator-online
 */
@RestController
@RequestMapping("${hsweb.web.mappings.oauth2-server-config:oauth2-server-config}")
@Authorize(permission = "oauth2-server-config")
@Api(tags = "OAuth2.0-服务配置")
public class OAuth2ServerConfigController implements GenericEntityController<OAuth2ServerConfigEntity, String, QueryParamEntity, OAuth2ServerConfigEntity> {

    private OAuth2ServerConfigService oAuth2ServerConfigService;

    @Override
    public OAuth2ServerConfigEntity modelToEntity(OAuth2ServerConfigEntity model, OAuth2ServerConfigEntity entity) {
        return model;
    }

    @Autowired
    public void setOAuth2ServerConfigService(OAuth2ServerConfigService oAuth2ServerConfigService) {
        this.oAuth2ServerConfigService = oAuth2ServerConfigService;
    }

    @Override
    public OAuth2ServerConfigService getService() {
        return oAuth2ServerConfigService;
    }
}
