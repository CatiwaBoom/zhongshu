package org.cycle.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.cycle.workflow.dto.PublishWorkflowDefinitionRequest;
import org.cycle.workflow.dto.SaveWorkflowDesignRequest;
import org.cycle.workflow.dto.UpdateWorkflowDefinitionRequest;
import org.cycle.workflow.dto.WorkflowDesignResponse;
import org.cycle.workflow.entity.WorkflowDefinitionEntity;

public interface WorkflowDefinitionService extends IService<WorkflowDefinitionEntity> {
    // 分页查询流程定义，前端可通过 keyword/page/size 控制结果集
    Page<WorkflowDefinitionEntity> listDefinitions(String keyword, Integer page, Integer size);

    WorkflowDefinitionEntity publish(PublishWorkflowDefinitionRequest request);

    WorkflowDefinitionEntity updateDefinition(String id, UpdateWorkflowDefinitionRequest request);

    boolean removeDefinition(String id);

    WorkflowDesignResponse saveDesign(String definitionId, SaveWorkflowDesignRequest request);

    WorkflowDesignResponse getDesign(String definitionId);

    WorkflowDesignResponse deleteNode(String definitionId, String nodeId);

    String nextCode();
}

