package org.cycle.workflow.dto;

import lombok.Data;

@Data
public class SaveWorkflowDesignRequest {
    /**
     * 前端可能传 JSON 对象，也可能传 JSON 字符串，这里用 Object 统一接收。
     */
    private Object designJson;
}

