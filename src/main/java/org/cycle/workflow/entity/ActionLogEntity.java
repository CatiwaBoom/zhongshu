package org.cycle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

/**
 * 流程动作日志实体。
 */
@Data
@TableName("wf_action_log")
public class ActionLogEntity extends BaseEntity {
    /** 流程实例ID */
    private String instanceId;
    /** 任务ID */
    private String taskId;
    /** 动作类型 */
    private String actionType;
    /** 操作人 */
    private String operator;
    /** 动作备注 */
    private String actionComment;
    /** 快照JSON */
    private String snapshotJson;
}
