package org.cycle.workflow.dto;

import lombok.Data;

/**
 * 任务动作请求。
 */
@Data
public class TaskActionRequest {
    /** 幂等请求ID */
    private String requestId;
    /** 流程实例ID */
    private String instanceId;
    /** 任务ID */
    private String taskId;
    /** 动作类型：APPROVE/REJECT/TRANSFER/WITHDRAW/TERMINATE */
    private String action;
    /** 当前操作人 */
    private String operator;
    /** 移交目标人（TRANSFER时必填） */
    private String targetUser;
    /** 动作备注 */
    private String comment;
}
