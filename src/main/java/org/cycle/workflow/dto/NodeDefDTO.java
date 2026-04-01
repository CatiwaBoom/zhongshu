package org.cycle.workflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 节点定义入参。
 */
@Data
public class NodeDefDTO {
    /** 节点编码（定义内唯一） */
    private String nodeKey;
    /** 节点名称 */
    private String nodeName;
    /** 审批模式：BASIC/OR_SIGN/COUNTER_SIGN */
    private String approvalMode;
    /** 节点审批人账号列表 */
    private List<String> assignees;
}
