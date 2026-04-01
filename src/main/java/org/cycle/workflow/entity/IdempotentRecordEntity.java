package org.cycle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

/**
 * 幂等记录实体。
 */
@Data
@TableName("wf_idempotent")
public class IdempotentRecordEntity extends BaseEntity {
    /** 请求幂等ID */
    private String requestId;
    /** 动作类型 */
    private String actionType;
    /** 流程实例ID */
    private String instanceId;
    /** 响应快照 */
    private String responseJson;
}
