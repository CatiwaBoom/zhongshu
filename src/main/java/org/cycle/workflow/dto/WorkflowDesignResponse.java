package org.cycle.workflow.dto;

import lombok.Data;

@Data
public class WorkflowDesignResponse {
    private String definitionId;
    private Object designJson;
    private Integer versionNo;
}

