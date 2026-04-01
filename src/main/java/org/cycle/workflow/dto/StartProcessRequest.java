package org.cycle.workflow.dto;

import lombok.Data;

/**
 * 发起流程请求。
 */
@Data
public class StartProcessRequest {
    /** 幂等请求ID，建议调用方全局唯一 */
    private String requestId;
    /** 流程定义编码 */
    private String definitionCode;
    /** 业务类型 */
    private String bizType;
    /** 业务单据ID */
    private String bizId;
    /** 发起人 */
    private String starter;
    /** 流程变量JSON */
    private String variablesJson;
}
