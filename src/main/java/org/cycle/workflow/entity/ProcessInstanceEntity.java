package org.cycle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

/**
 * 流程实例实体。
 */
@Data
@TableName("wf_process_inst")
public class ProcessInstanceEntity extends BaseEntity {
    /** 定义ID */
    private String defId;
    /** 流程编码 */
    private String defCode;
    /** 定义版本号 */
    private Integer defVersion;
    /** 业务类型 */
    private String bizType;
    /** 业务ID */
    private String bizId;
    /** 流程状态 */
    private String state;
    /** 当前节点编码 */
    private String currentNodeKey;
    /** 发起人 */
    private String starter;
    /** 流程变量JSON */
    private String variablesJson;
    /** 乐观锁版本号 */
    private Integer revision;
}
