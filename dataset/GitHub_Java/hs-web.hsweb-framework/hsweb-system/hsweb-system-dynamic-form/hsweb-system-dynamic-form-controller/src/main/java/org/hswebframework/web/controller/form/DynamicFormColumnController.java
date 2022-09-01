package org.hswebframework.web.controller.form;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.hswebframework.web.authorization.Permission;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.controller.message.ResponseMessage;
import org.hswebframework.web.entity.form.DynamicFormColumnEntity;
import org.hswebframework.web.logging.AccessLogger;
import org.hswebframework.web.service.form.DynamicFormService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 动态表单
 *
 * @author hsweb-generator-online
 */
@RestController
@RequestMapping("${hsweb.web.mappings.dynamic/form/column:dynamic/form/column}")
@Authorize(permission = "dynamic-form")
@AccessLogger("动态表单")
@Api(tags = "dynamic-form", description = "动态表单")
public class DynamicFormColumnController {

    private DynamicFormService dynamicFormService;

    @Autowired
    public void setDynamicFormService(DynamicFormService dynamicFormService) {
        this.dynamicFormService = dynamicFormService;
    }

    @PatchMapping("/batch")
    @Authorize(action = Permission.ACTION_ADD)
    @AccessLogger("保存多个表单列")
    @ApiOperation("保存多个表单列")
    public ResponseMessage<List<String>> add(@RequestBody List<DynamicFormColumnEntity> columnEntities) {
        return ResponseMessage.ok(dynamicFormService.saveOrUpdateColumn(columnEntities));
    }

    @PatchMapping
    @Authorize(action = Permission.ACTION_ADD)
    @AccessLogger("保存表单列")
    @ApiOperation("保存表单列")
    public ResponseMessage<String> add(@RequestBody DynamicFormColumnEntity columnEntity) {
        return ResponseMessage.ok(dynamicFormService.saveOrUpdateColumn(columnEntity));
    }

    @DeleteMapping
    @Authorize(action = Permission.ACTION_DELETE)
    @AccessLogger("删除列")
    @ApiOperation("删除列")
    public ResponseMessage<DynamicFormColumnEntity> delete(String id) {
        return ResponseMessage.ok(dynamicFormService.deleteColumn(id));
    }

    @DeleteMapping("/batch")
    @Authorize(action = Permission.ACTION_DELETE)
    @AccessLogger("删除多列")
    @ApiOperation("删除多列")
    public ResponseMessage<List<DynamicFormColumnEntity>> delete(List<String> id) {
        return ResponseMessage.ok(dynamicFormService.deleteColumn(id));
    }

    @GetMapping("/{formId}")
    @Authorize(action = Permission.ACTION_GET)
    @AccessLogger("获取表单的所有列")
    @ApiOperation("获取表单的所有列")
    public ResponseMessage<List<DynamicFormColumnEntity>> getByFormId(@PathVariable String formId) {
        return ResponseMessage.ok(dynamicFormService.selectColumnsByFormId(formId));
    }

}
