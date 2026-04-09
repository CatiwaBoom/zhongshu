package org.cycle.workflow.dto;

import lombok.Data;

@Data
public class PublishWorkflowDefinitionRequest {
    private String code;
    private String name;
    private String description;
    private Integer status;
}

