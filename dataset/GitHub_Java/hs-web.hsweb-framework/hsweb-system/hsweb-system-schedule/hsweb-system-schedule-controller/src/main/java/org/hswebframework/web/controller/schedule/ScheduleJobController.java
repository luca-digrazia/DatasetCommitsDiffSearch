package org.hswebframework.web.controller.schedule;

import org.hswebframework.web.authorization.Permission;
import org.hswebframework.web.authorization.annotation.Authorize;
import org.hswebframework.web.commons.entity.param.QueryParamEntity;
import org.hswebframework.web.controller.SimpleGenericEntityController;
import org.hswebframework.web.controller.message.ResponseMessage;
import org.hswebframework.web.entity.schedule.ScheduleJobEntity;
import org.hswebframework.web.logging.AccessLogger;
import  org.hswebframework.web.service.schedule.ScheduleJobService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *  调度任务
 *
 * @author hsweb-generator-online
 */
@RestController
@RequestMapping("${hsweb.web.mappings.scheduleJob:schedule/job}")
@Authorize(permission = "schedule-job")
@AccessLogger("调度任务")
public class ScheduleJobController implements SimpleGenericEntityController<ScheduleJobEntity, String, QueryParamEntity> {

    private ScheduleJobService scheduleJobService;
  
    @Autowired
    public void setScheduleJobService(ScheduleJobService scheduleJobService) {
        this.scheduleJobService = scheduleJobService;
    }
  
    @Override
    public ScheduleJobService getService() {
        return scheduleJobService;
    }

    @PutMapping("/{id}/enable")
    @Authorize(action = Permission.ACTION_ENABLE)
    @AccessLogger("启用")
    public ResponseMessage<Void> enable(@PathVariable String id){
        scheduleJobService.enable(id);
        return ResponseMessage.ok();
    }

    @PutMapping("/{id}/disable")
    @Authorize(action = Permission.ACTION_DISABLE)
    @AccessLogger("禁用")
    public ResponseMessage<Void> disable(@PathVariable String id){
        scheduleJobService.disable(id);
        return ResponseMessage.ok();
    }
}
