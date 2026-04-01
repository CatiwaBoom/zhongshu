package org.cycle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

/**
 * 流程节点定义实体。
 */
@Data
@TableName("wf_process_node_def")
public class ProcessNodeDefinitionEntity extends BaseEntity {
    /** 流程定义ID */
    private String defId;
    /** 节点编码 */
    private String nodeKey;
    /** 节点名称 */
    private String nodeName;
    /** 节点顺序号 */
    private Integer sortNo;
    /** 审批模式 */
    private String approvalMode;
    /** 审批人表达式 */
    private String assigneeExpr;
}
