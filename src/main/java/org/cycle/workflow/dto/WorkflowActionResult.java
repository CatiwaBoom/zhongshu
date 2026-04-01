package org.cycle.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 流程动作执行结果。
 */
@Data
@AllArgsConstructor
public class WorkflowActionResult {
    /** 流程实例ID */
    private String instanceId;
    /** 最新流程状态 */
    private String processState;
    /** 当前节点编码，流程结束时可能为空 */
    private String currentNodeKey;
    /** 结果提示信息 */
    private String message;
}
