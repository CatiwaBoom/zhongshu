package org.cycle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

@Data
@TableName("WF_DEFINITION")
public class WorkflowDefinitionEntity extends BaseEntity {
    private String code;
    private String name;
    private String description;
    private Integer status;
    private Integer versionNo;
}

