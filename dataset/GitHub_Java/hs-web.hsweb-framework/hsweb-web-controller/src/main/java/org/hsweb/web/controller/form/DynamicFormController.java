package org.hsweb.web.controller.form;

import org.hsweb.web.bean.common.InsertMapParam;
import org.hsweb.web.bean.common.QueryParam;
import org.hsweb.web.bean.common.UpdateMapParam;
import org.hsweb.web.core.authorize.annotation.Authorize;
import org.hsweb.web.core.logger.annotation.AccessLogger;
import org.hsweb.web.core.message.ResponseMessage;
import org.hsweb.web.service.form.DynamicFormService;
import org.hsweb.web.service.form.FormService;
import org.hsweb.web.service.resource.FileService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by zhouhao on 16-4-23.
 */
@RestController
@RequestMapping(value = "/dyn-form")
@AccessLogger("动态表单")
public class DynamicFormController {

    @Resource
    private DynamicFormService dynamicFormService;

    @Resource
    private FormService formService;

    @Resource
    private FileService fileService;

    @RequestMapping(value = "/deployed/{name}", method = RequestMethod.GET)
    @Authorize(expression = "#dynamicFormAuthorizeValidator.validate(#name,#user,'R')")
    public ResponseMessage deployed(@PathVariable("name") String name) throws Exception {
        return ResponseMessage.ok(formService.selectDeployed(name));
    }

    @RequestMapping(value = "/{name}", method = RequestMethod.GET)
    @AccessLogger("查看列表")
    @Authorize(expression = "#dynamicFormAuthorizeValidator.validate(#name,#user,'R')")
    public ResponseMessage list(@PathVariable("name") String name,
                                QueryParam param) throws Exception {
        // 获取条件查询
        Object data;
        if (!param.isPaging())//不分页
            data = dynamicFormService.select(name, param);
        else
            data = dynamicFormService.selectPager(name, param);
        return ResponseMessage.ok(data)
                .onlyData();
    }

    @RequestMapping(value = "/{name}/{primaryKey}", method = RequestMethod.GET)
    @AccessLogger("按主键查询")
    @Authorize(expression = "#dynamicFormAuthorizeValidator.validate(#name,#user,'R')")
    public ResponseMessage info(@PathVariable("name") String name,
                                @PathVariable("primaryKey") String primaryKey) throws Exception {
        Map<String, Object> data = dynamicFormService.selectByPk(name, primaryKey);
        return ResponseMessage.ok(data);
    }

    @RequestMapping(value = "/{name}", method = RequestMethod.POST)
    @AccessLogger("新增数据")
    @Authorize(expression = "#dynamicFormAuthorizeValidator.validate(#name,#user,'C')")
    public ResponseMessage insert(@PathVariable("name") String name,
                                  @RequestBody(required = true) Map<String, Object> data) throws Exception {
        String pk = dynamicFormService.insert(name, new InsertMapParam(data));
        return ResponseMessage.ok(pk);
    }

    @RequestMapping(value = "/{name}/{primaryKey}", method = RequestMethod.PUT)
    @AccessLogger("更新数据")
    @Authorize(expression = "#dynamicFormAuthorizeValidator.validate(#name,#user,'U')")
    public ResponseMessage update(@PathVariable("name") String name,
                                  @PathVariable("primaryKey") String primaryKey,
                                  @RequestBody(required = true) Map<String, Object> data) throws Exception {
        int i = dynamicFormService.updateByPk(name, primaryKey, new UpdateMapParam(data));
        return ResponseMessage.ok(i);
    }

    @RequestMapping(value = "/{name}/{primaryKey}", method = RequestMethod.DELETE)
    @AccessLogger("删除数据")
    @Authorize(expression = "#dynamicFormAuthorizeValidator.validate(#name,#user,'D')")
    public ResponseMessage delete(@PathVariable("name") String name,
                                  @PathVariable("primaryKey") String primaryKey) throws Exception {
        dynamicFormService.deleteByPk(name, primaryKey);
        return ResponseMessage.ok();
    }

    @RequestMapping(value = "/{name}/export/{fileName:.+}", method = RequestMethod.GET)
    @AccessLogger("导出excel")
    @Authorize(expression = "#dynamicFormAuthorizeValidator.validate(#name,#user,'export')")
    public void exportExcel(@PathVariable("name") String name,
                            @PathVariable("fileName") String fileName,
                            QueryParam queryParam,
                            HttpServletResponse response) throws Exception {
        response.setHeader("Content-disposition", "attachment;filename=" + URLEncoder.encode(fileName, "utf-8"));
        response.setContentType("application/vnd.ms-excel");
        dynamicFormService.exportExcel(name, queryParam, response.getOutputStream());
    }

    @RequestMapping(value = "/{name}/import/{fileId:.+}", method = {RequestMethod.PATCH})
    @AccessLogger("导入为excel")
    @Authorize(expression = "#dynamicFormAuthorizeValidator.validate(#name,#user,'import')")
    public ResponseMessage importExcel(@PathVariable("name") String name,
                                       @PathVariable("fileId") String fileId) throws Exception {
        String[] ids = fileId.split("[,]");
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < ids.length; i++) {
            InputStream inputStream = fileService.readResources(ids[i]);
            result.put(ids[i], dynamicFormService.importExcel(name, inputStream));
        }
        return ResponseMessage.ok(result);
    }

}
