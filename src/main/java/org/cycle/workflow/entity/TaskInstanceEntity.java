package org.cycle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.sql.Timestamp;

/**
 * 任务实例实体。
 */
@Data
@TableName("wf_task_inst")
public class TaskInstanceEntity extends BaseEntity {
    /** 所属流程实例ID */
    private String instanceId;
    /** 所属节点编码 */
    private String nodeKey;
    /** 当前处理人 */
    private String assignee;
    /** 任务状态 */
    private String state;
    /** 投票结果 */
    private String voteResult;
    /** 移交来源人 */
    private String delegatedFrom;
    /** 完成时间 */
    private Timestamp completedAt;
    /** 乐观锁版本号 */
    private Integer revision;
}
