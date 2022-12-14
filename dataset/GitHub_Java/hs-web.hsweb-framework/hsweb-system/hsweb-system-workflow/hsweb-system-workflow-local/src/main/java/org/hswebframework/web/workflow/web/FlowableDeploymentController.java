package org.hswebframework.web.workflow.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.SneakyThrows;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.repository.ProcessDefinitionQuery;
import org.apache.commons.io.FilenameUtils;
import org.hswebframework.web.NotFoundException;
import org.hswebframework.web.authorization.Permission;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.commons.entity.PagerResult;
import org.hswebframework.web.commons.entity.param.QueryParamEntity;
import org.hswebframework.web.controller.message.ResponseMessage;
import org.hswebframework.web.workflow.service.BpmActivityService;
import org.hswebframework.web.workflow.service.BpmProcessService;
import org.hswebframework.web.workflow.service.BpmTaskService;
import org.hswebframework.web.workflow.service.imp.AbstractFlowableService;
import org.hswebframework.web.workflow.util.QueryUtils;
import org.hswebframework.web.workflow.web.response.ActivityInfo;
import org.hswebframework.web.workflow.web.response.ProcessDefinitionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.zip.ZipInputStream;

/**
 * @Author wangwei
 * @Date 2017/8/10.
 */
@RestController
@RequestMapping("/workflow/process/definition")
@Api(tags = "?????????-??????????????????", description = "???????????????????????????")
@Authorize(permission = "workflow-definition", description = "?????????-??????????????????")
public class FlowableDeploymentController extends AbstractFlowableService {

    private final static String MODEL_ID = "modelId";

    @Autowired
    BpmTaskService     bpmTaskService;
    @Autowired
    BpmProcessService  bpmProcessService;
    @Autowired
    BpmActivityService bpmActivityService;

    /**
     * ??????????????????
     */
    @GetMapping
    @ApiOperation("????????????????????????")
    @Authorize(action = Permission.ACTION_QUERY)
    public ResponseMessage<PagerResult<ProcessDefinitionInfo>> queryProcessList(QueryParamEntity param) {
        ProcessDefinitionQuery processDefinitionQuery = repositoryService.createProcessDefinitionQuery();

        return ResponseMessage.ok(QueryUtils.doQuery(processDefinitionQuery, param, ProcessDefinitionInfo::of));
    }

    /**
     * ??????????????????
     * ??????ZIP??????????????????
     */
    @PostMapping(value = "/deploy")
    @ApiOperation("???????????????????????????????????????")
    @Authorize(action = "deploy")
    public ResponseMessage<Deployment> deploy(@RequestPart(value = "file") MultipartFile file) throws IOException {
        // ????????????????????????
        String fileName = file.getOriginalFilename();

        // ????????????????????????????????????
        InputStream fileInputStream = file.getInputStream();

        // ??????????????????
        String extension = FilenameUtils.getExtension(fileName);

        // zip??????bar??????????????????ZipInputStream????????????
        DeploymentBuilder deployment = repositoryService.createDeployment();
        if ("zip".equals(extension) || "bar".equals(extension)) {
            ZipInputStream zip = new ZipInputStream(fileInputStream);
            deployment.addZipInputStream(zip);
        } else {
            // ?????????????????????????????????
            deployment.addInputStream(fileName, fileInputStream);
        }
        Deployment result = deployment.deploy();

        return ResponseMessage.ok(result);
    }

    /**
     * ??????????????????
     *
     * @param processDefinitionId ????????????ID
     * @param resourceName        ????????????
     */
    @GetMapping(value = "/{processDefinitionId}/resource/{resourceName}")
    @ApiOperation("??????????????????")
    @Authorize(action = Permission.ACTION_QUERY)
    @SneakyThrows
    public void readResource(@PathVariable String processDefinitionId
            , @PathVariable String resourceName, HttpServletResponse response) {
        ProcessDefinitionQuery pdq = repositoryService.createProcessDefinitionQuery();
        ProcessDefinition pd = pdq.processDefinitionId(processDefinitionId).singleResult();

        // ??????????????????
        try (InputStream resourceAsStream = repositoryService.getResourceAsStream(pd.getDeploymentId(), resourceName)) {
            StreamUtils.copy(resourceAsStream, response.getOutputStream());
        }

    }

    /***
     * ??????????????????Model
     */
    @PutMapping(value = "/convert-to-model/{processDefinitionId}")
    @ApiOperation("????????????????????????")
    @Authorize(action = Permission.ACTION_UPDATE)
    public ResponseMessage<String> convertToModel(@PathVariable("processDefinitionId") String processDefinitionId)
            throws UnsupportedEncodingException, XMLStreamException {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId).singleResult();
        if (null == processDefinition) {
            throw new NotFoundException();
        }
        InputStream bpmnStream = repositoryService.getResourceAsStream(processDefinition.getDeploymentId(),
                processDefinition.getResourceName());

        XMLInputFactory xif = XMLInputFactory.newInstance();
        InputStreamReader in = new InputStreamReader(bpmnStream, "UTF-8");
        XMLStreamReader xtr = xif.createXMLStreamReader(in);
        BpmnModel bpmnModel = new BpmnXMLConverter().convertToBpmnModel(xtr);


        BpmnJsonConverter converter = new BpmnJsonConverter();
        com.fasterxml.jackson.databind.node.ObjectNode modelNode = converter.convertToJson(bpmnModel);
        org.activiti.engine.repository.Model modelData = repositoryService.newModel();
        modelData.setKey(processDefinition.getKey());
        modelData.setName(processDefinition.getResourceName().substring(0, processDefinition.getResourceName().indexOf(".")));
        modelData.setCategory(processDefinition.getDeploymentId());

        ObjectNode modelObjectNode = new ObjectMapper().createObjectNode();
        modelObjectNode.put(ModelDataJsonConstants.MODEL_NAME, processDefinition.getName());
        modelObjectNode.put(ModelDataJsonConstants.MODEL_REVISION, 1);
        modelObjectNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, processDefinition.getDescription());
        modelData.setMetaInfo(modelObjectNode.toString());

        repositoryService.saveModel(modelData);

        repositoryService.addModelEditorSource(modelData.getId(), modelNode.toString().getBytes("utf-8"));
        return ResponseMessage.ok(modelData.getId());
    }

    /**
     * ?????????????????????,??????????????????????????????????????????????????????
     *
     * @param deploymentId ????????????ID
     */
    @DeleteMapping(value = "/deployment/{deploymentId}")
    @ApiOperation("?????????????????????")
    @Authorize(action = Permission.ACTION_DELETE)
    public ResponseMessage<Void> deleteProcessDefinition(
            @PathVariable("deploymentId") String deploymentId
            , @RequestParam(defaultValue = "false") boolean cascade) {
        repositoryService.deleteDeployment(deploymentId, cascade);
        
        return ResponseMessage.ok();
    }


    /**
     * ???????????????????????????
     */
    @GetMapping("/{processInstanceId}/activity")
    @ApiOperation("???????????????????????????????????????")
    @Authorize(action = Permission.ACTION_QUERY)
    public ResponseMessage<ActivityInfo> getProcessInstanceActivity(@PathVariable String processInstanceId) {
        HistoricProcessInstance processInstance = bpmTaskService.selectHisProInst(processInstanceId);
        if (processInstance != null) {
            ActivityImpl activity = bpmActivityService.getActivityByProcInstId(processInstance.getProcessDefinitionId(), processInstance.getId());
            return ResponseMessage.ok(ActivityInfo.of(activity));
        } else {
            throw new NotFoundException("???????????????");
        }
    }

    @GetMapping("/{processInstanceId}/image")
    @ApiOperation("???????????????????????????????????????")
    @Authorize(action = Permission.ACTION_QUERY)
    public void getProcessImage(@PathVariable String processInstanceId, HttpServletResponse response) throws IOException {
        try (InputStream inputStream = bpmProcessService.findProcessPic(processInstanceId)) {
            response.setContentType(MediaType.IMAGE_PNG_VALUE);
            ImageIO.write(ImageIO.read(inputStream), "png", response.getOutputStream());
        }
    }
}
