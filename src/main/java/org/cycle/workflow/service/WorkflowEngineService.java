package org.cycle.workflow.service;

import org.cycle.workflow.dto.PublishDefinitionRequest;
import org.cycle.workflow.dto.StartProcessRequest;
import org.cycle.workflow.dto.TaskActionRequest;
import org.cycle.workflow.dto.WorkflowActionResult;
import org.cycle.workflow.entity.ProcessInstanceEntity;

/**
 * 流程引擎服务对外契约。
 */
public interface WorkflowEngineService {

    /**
     * 发布流程定义并生成新版本。
     */
    String publishDefinition(PublishDefinitionRequest request);

    /**
     * 发起流程实例。
     */
    WorkflowActionResult startProcess(StartProcessRequest request);

    /**
     * 执行任务动作（审批/驳回/移交/撤回/终止）。
     */
    WorkflowActionResult handleTaskAction(TaskActionRequest request);

    /**
     * 查询流程实例。
     */
    ProcessInstanceEntity getInstance(String instanceId);

    /**
     * 获取下一个流程定义编码（用于前端预览与创建前获取唯一编码）。
     * 格式 WF_YYYYMMDD_XXX
     */
    String getNextDefinitionCode();
}
