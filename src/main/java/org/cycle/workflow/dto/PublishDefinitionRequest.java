package org.cycle.workflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 发布流程定义请求。
 */
@Data
public class PublishDefinitionRequest {
    /** 流程编码，同一流程跨版本保持不变 */
    private String code;
    /** 流程名称 */
    private String name;
    /** 备注 */
    private String remark;
    /** 操作人 */
    private String operator;
    /** 按顺序配置的节点定义列表 */
    private List<NodeDefDTO> nodes;
}
