package org.cycle.workflow.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.cycle.workflow.dto.PublishWorkflowDefinitionRequest;
import org.cycle.workflow.dto.SaveWorkflowDesignRequest;
import org.cycle.workflow.dto.UpdateWorkflowDefinitionRequest;
import org.cycle.workflow.dto.WorkflowDesignResponse;
import org.cycle.workflow.entity.WorkflowDefinitionEntity;

import java.util.List;

public interface WorkflowDefinitionService extends IService<WorkflowDefinitionEntity> {
    List<WorkflowDefinitionEntity> listDefinitions(String keyword);

    WorkflowDefinitionEntity publish(PublishWorkflowDefinitionRequest request);

    WorkflowDefinitionEntity updateDefinition(String id, UpdateWorkflowDefinitionRequest request);

    boolean removeDefinition(String id);

    WorkflowDesignResponse saveDesign(String definitionId, SaveWorkflowDesignRequest request);

    WorkflowDesignResponse getDesign(String definitionId);

    WorkflowDesignResponse deleteNode(String definitionId, String nodeId);

    String nextCode();
}

