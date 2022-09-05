/*
 * Copyright 2015-2016 https://github.com/hs-web
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.hsweb.web.controller.login;

import org.hsweb.commons.StringUtils;
import org.hsweb.web.bean.common.QueryParam;
import org.hsweb.web.bean.po.module.Module;
import org.hsweb.web.bean.po.user.User;
import org.hsweb.web.core.authorize.annotation.Authorize;
import org.hsweb.web.core.logger.annotation.AccessLogger;
import org.hsweb.web.core.message.ResponseMessage;
import org.hsweb.web.core.utils.WebUtil;
import org.hsweb.web.service.module.ModuleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户模块控制器,用于获取当前登录用户持有的模块权限
 */
@RestController
@RequestMapping("/userModule")
public class UserModuleController {
    @Resource
    public ModuleService moduleService;

    @RequestMapping
    @Authorize
    @AccessLogger("用户模块信息")
    public ResponseMessage userModule() throws Exception {
        String[] includes = {
                "name", "id", "parentId", "icon", "uri", "optional"
        };
        User user = WebUtil.getLoginUser();
        List<Module> modules;
        if (user == null) {
            QueryParam queryParam = new QueryParam();
            queryParam.includes(includes).orderBy("sortIndex");
            modules = moduleService.select(queryParam);
            modules = modules.stream()
                    .filter(module -> {
                        Object obj = module.getOptionalMap().get("M");
                        if (obj instanceof Map)
                            return StringUtils.isTrue(((Map) obj).get("checked"));
                        return false;
                    })
                    .collect(Collectors.toCollection(() -> new LinkedList<>()));
        } else {
            modules = user.getModules().stream()
                    .filter(module -> user.hasAccessModuleAction(module.getId(), "M"))
                    .sorted()
                    .collect(Collectors.toList());
        }

        return ResponseMessage.ok(modules)
                .include(Module.class, includes)
                .exclude(Module.class, "optional")
                .onlyData();
    }
}
