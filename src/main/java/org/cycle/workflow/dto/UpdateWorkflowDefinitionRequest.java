package org.cycle.workflow.dto;

import lombok.Data;

@Data
public class UpdateWorkflowDefinitionRequest {
    private String name;
    private String description;
    private Integer status;
}

