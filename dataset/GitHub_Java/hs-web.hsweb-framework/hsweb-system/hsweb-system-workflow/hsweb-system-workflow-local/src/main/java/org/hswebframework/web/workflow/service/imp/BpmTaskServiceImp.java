package org.hswebframework.web.workflow.service.imp;

import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricTaskInstance;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.TaskServiceImpl;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.PvmActivity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.delegate.ActivityExecution;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.pvm.process.TransitionImpl;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.hswebframework.utils.StringUtils;
import org.hswebframework.web.NotFoundException;
import org.hswebframework.web.workflow.service.BpmActivityService;
import org.hswebframework.web.workflow.service.BpmTaskService;
import org.hswebframework.web.workflow.service.BpmUtilsService;
import org.hswebframework.web.workflow.flowable.utils.JumpTaskCmd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

import static org.hswebframework.web.commons.entity.param.QueryParamEntity.single;

/**
 * @Author wangwei
 * @Date 2017/8/7.
 */
@Service
@Transactional(rollbackFor = Throwable.class)
public class BpmTaskServiceImp extends AbstractFlowableService implements BpmTaskService {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    @Resource
    private BpmActivityService bpmActivityService;
    @Autowired
    BpmUtilsService bpmUtilsService;

    @Override
    public List<Task> selectNowTask(String procInstId) {
        return taskService.createTaskQuery().processInstanceId(procInstId).active().list();
    }

    @Override
    public List<Task> selectTaskByProcessId(String procInstId) {
        return taskService.createTaskQuery().processInstanceId(procInstId).active().list();
    }

    @Override
    public Task selectTaskByTaskId(String taskId) {
        return taskService.createTaskQuery().taskId(taskId).active().singleResult();
    }

    @Override
    public String selectNowTaskName(String procInstId) {
        List<Task> tasks = selectNowTask(procInstId);
        if (tasks.size() == 1) {
            return tasks.get(0).getName();
        } else {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < tasks.size(); i++) {
                if (i != 0) {
                    builder.append(",");
                }
                builder.append(tasks.get(i).getName());
            }
            return builder.toString();
        }

    }

    @Override
    public String selectNowTaskId(String procInstId) {
        List<Task> tasks = selectNowTask(procInstId);
        if (tasks.size() == 1) {
            return tasks.get(0).getId();
        } else {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < tasks.size(); i++) {
                if (i != 0) {
                    builder.append(",");
                }
                builder.append(tasks.get(i).getId());
            }
            return builder.toString();
        }
    }

    @Override
    public void claim(String taskId, String userId) {
        Task task = taskService.createTaskQuery().taskId(taskId).taskCandidateUser(userId).active().singleResult();
        if (task == null) {
            logger.warn("??????????????????!");
            throw new NotFoundException("task not found");
            //return; // fix null point
        }
        if (!StringUtils.isNullOrEmpty(task.getAssignee())) {
            logger.warn("?????????????????????!");
        } else {
            taskService.claim(taskId, userId);
        }
    }


    @Override
    public List<Task> claimList(String userId) {
        // ?????????????????????
        List<Task> claimList = taskService.createTaskQuery()
                .taskCandidateUser(userId)
                .includeProcessVariables()
                .active()
                .list();
        return claimList;
    }

    @Override
    public List<Task> todoList(String userId) {
        // ?????????????????????
        List<Task> todoList = taskService.createTaskQuery()
                .taskAssignee(userId)
                .includeProcessVariables()
                .active()
                .list();
        return todoList;
    }

    @Override
    public void complete(String taskId, String userId, String activityId, String nextClaim) {
        Task task = taskService.createTaskQuery().taskId(taskId).includeProcessVariables().active().singleResult();
        if (StringUtils.isNullOrEmpty(task)) {
            logger.warn("???????????????!");
            throw new NotFoundException("task not found");
        }
        String assignee = task.getAssignee();
        if (StringUtils.isNullOrEmpty(assignee)) {
            logger.warn("??????????????????!");
            throw new NotFoundException("Please sign for the task first");
        }
        if (!userId.equals(assignee)) {
            logger.warn("???????????????????????????");
            throw new NotFoundException("You can only do your own work");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("oldTaskId", task.getId());
        //???????????????
        if (StringUtils.isNullOrEmpty(activityId)) {
            taskService.complete(taskId, map);
        } else {
            jumpTask(taskId, activityId, nextClaim);
        }
        //????????????ID?????????????????????????????????????????????,??????????????????????????????????????????
        List<Execution> execution = runtimeService.createExecutionQuery().processInstanceId(task.getProcessInstanceId()).list();
        if (execution.size() > 0) {
            List<Task> tasks = selectNowTask(task.getProcessInstanceId());
            // ????????????????????????
            if (tasks.size() == 1 && !StringUtils.isNullOrEmpty(nextClaim)) {
                claim(tasks.get(0).getId(), nextClaim);
            } else {
                for (Task t : tasks) {
                    addCandidateUser(t.getId(), t.getTaskDefinitionKey(), userId);
                }
            }
        }
    }

    @Override
    public void reject(String taskId) {
        // ?????????????????????????????????
        String oldTaskId = selectVariableLocalByTaskId(taskId, "oldTaskId").toString();
        HistoricTaskInstance taskInstance = historyService.createHistoricTaskInstanceQuery().taskId(oldTaskId).singleResult();
        if (taskInstance == null) {
            throw new NotFoundException("???????????????????????????,taskId:" + oldTaskId);
        }

        Task task = selectTaskByTaskId(taskId);

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();
        if (processInstance == null) {
            throw new NotFoundException("??????????????????");
        }

        Map<String, Object> variables = processInstance.getProcessVariables();

        ProcessDefinitionEntity definition = (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(task.getProcessDefinitionId());
        if (definition == null) {
            throw new NotFoundException("?????????????????????");
        }

        ActivityExecution execution = (ActivityExecution) runtimeService.createExecutionQuery()
                .executionId(task.getExecutionId()).singleResult();
        // ??????????????????
        if (execution.isConcurrent()) {
            throw new NotFoundException("???????????????????????????,taskId:" + task.getId());
        }

        // ????????????????????????
        long num = managementService.createJobQuery().executionId(task.getExecutionId()).count();
        if (num > 0) {
            throw new NotFoundException("???????????????????????????");
        }

        // ??????


        // ?????????????????????
        ActivityImpl currActivity = definition.findActivity(task.getTaskDefinitionKey());
        List<PvmTransition> nextTransitionList = currActivity.getIncomingTransitions();
        // ???????????????????????????
        List<PvmTransition> oriPvmTransitionList = new ArrayList<>();
        List<PvmTransition> pvmTransitionList = currActivity.getOutgoingTransitions();
        oriPvmTransitionList.addAll(pvmTransitionList);
        pvmTransitionList.clear();

        // ???????????????
        List<TransitionImpl> newTransitions = new ArrayList<>();
        for (PvmTransition nextTransition : nextTransitionList) {
            PvmActivity nextActivity = nextTransition.getSource();
            ActivityImpl nextActivityImpl = definition.findActivity(nextActivity.getId());
            TransitionImpl newTransition = currActivity.createOutgoingTransition();
            newTransition.setDestination(nextActivityImpl);
            newTransitions.add(newTransition);
        }
        // ????????????
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstance.getId())
                .taskDefinitionKey(task.getTaskDefinitionKey()).list();
        for (Task t : tasks) {
            taskService.complete(t.getId(), variables);
            historyService.deleteHistoricTaskInstance(t.getId());
        }
        // ????????????
        for (TransitionImpl transitionImpl : newTransitions) {
            currActivity.getOutgoingTransitions().remove(transitionImpl);
        }
        pvmTransitionList.addAll(oriPvmTransitionList);

    }

    @Override
    public void jumpTask(String taskId, String activity, String next_claim) {
        Task task = selectTaskByTaskId(taskId);
        TaskServiceImpl taskServiceImpl = (TaskServiceImpl) taskService;
        taskServiceImpl.getCommandExecutor().execute(new JumpTaskCmd(task.getExecutionId(), activity));
        if(!StringUtils.isNullOrEmpty(next_claim)){
            task = selectTaskByTaskId(taskId);
            if (null != task) {
                claim(task.getId(), next_claim);
            }
        }
    }

    @Override
    public void addCandidateUser(String taskId, String actId, String userId) {
        if (!StringUtils.isNullOrEmpty(actId)) {
            // ????????????????????????
//            ActDefEntity actDefEntity = actDefService.selectSingle(single(ActDefEntity.actId, actId));
            // ??????????????????  ?????????????????? ???????????????
//            if (actDefEntity!=null) {
//                List<String> list = bpmUtilsService.selectUserIdsBy(userId,actDefEntity);
//                list.forEach(uId -> taskService.addCandidateUser(taskId,uId));
//            } else {
                taskService.addCandidateUser(taskId,
                        runtimeService.getIdentityLinksForProcessInstance(selectTaskByTaskId(taskId).getProcessInstanceId())
                        .stream()
                        .filter(linkEntity -> "starter".equals(linkEntity.getType()))
                        .findFirst().orElseThrow(()-> new NotFoundException("?????????????????????")).getUserId()
                );
//            }
        } else {
            taskService.addCandidateUser(taskId, userId);
        }
    }

    @Override
    public void setAssignee(String taskId, String userId) {
        taskService.setAssignee(taskId, userId);
    }

    @Override
    public void endProcess(String procInstId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery().processInstanceId(procInstId).singleResult();
        ActivityImpl activity = bpmActivityService.getEndEvent(processInstance.getProcessDefinitionId());
        jumpTask(procInstId, activity.getId(), null);
    }

    @Override
    public void removeHiTask(String taskId) {
        historyService.deleteHistoricTaskInstance(taskId);
    }

    @Override
    public Map<String, Object> selectVariableLocalByTaskId(String taskId) {
        return taskService.getVariablesLocal(taskId);
    }

    @Override
    public Object selectVariableLocalByTaskId(String taskId, String variableName) {
        return taskService.getVariableLocal(taskId, variableName);
    }

    @Override
    public HistoricProcessInstance selectHisProInst(String procInstId) {
        return historyService.createHistoricProcessInstanceQuery().processInstanceId(procInstId).singleResult();
    }

    @Override
    public ActivityImpl selectActivityImplByTask(String taskId) {
        if (StringUtils.isNullOrEmpty(taskId)) {
            return new ActivityImpl(null, null);
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        ProcessDefinitionEntity entity = (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(task.getProcessDefinitionId());
        List<ActivityImpl> activities = entity.getActivities();
        return activities
                .stream()
                .filter(activity -> "userTask".equals(activity.getProperty("type")) && activity.getProperty("name").equals(task.getName()))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("????????????????????????"));
    }

    @Override
    public Map<String, Object> getUserTasksByProcDefKey(String procDefKey) {
        String definitionId = repositoryService.createProcessDefinitionQuery().processDefinitionKey(procDefKey).orderByProcessDefinitionVersion().desc().list().get(0).getId();
        List<ActivityImpl> activities = bpmActivityService.getUserTasksByProcDefId(definitionId);
        Map<String, Object> map = new HashMap<>();
        for (ActivityImpl activity : activities) {
            map.put(activity.getId(), activity.getProperty("name"));
        }
        return map;
    }

    @Override
    public Map<String, Object> getUserTasksByProcInstId(String procInstId) {
        String definitionId = runtimeService.createProcessInstanceQuery().processInstanceId(procInstId).singleResult().getProcessDefinitionId();
        List<ActivityImpl> activities = bpmActivityService.getUserTasksByProcDefId(definitionId);
        Map<String, Object> map = new HashMap<>();
        for (ActivityImpl activity : activities) {
            map.put(activity.getId(), activity.getProperty("name"));
        }
        return map;
    }

    @Override
    public void setVariables(String taskId, Map<String, Object> map) {
        taskService.setVariables(taskId, map);
    }

    @Override
    public void removeVariables(String taskId, Collection<String> var2) {
        taskService.removeVariables(taskId, var2);
    }

    @Override
    public void setVariablesLocal(String taskId, Map<String, Object> map) {
        taskService.setVariablesLocal(taskId, map);
    }

    @Override
    public Map<String, Object> getVariablesByProcInstId(String procInstId) {
        List<Execution> executions = runtimeService.createExecutionQuery().processInstanceId(procInstId).list();
        String executionId = "";
        for (Execution execution : executions) {
            if (StringUtils.isNullOrEmpty(execution.getParentId())) {
                executionId = execution.getId();
            }
        }
        return runtimeService.getVariables(executionId);
    }

    @Override
    public Map<String, Object> getVariablesByTaskId(String taskId) {
        return taskService.getVariables(taskId);
    }
}
