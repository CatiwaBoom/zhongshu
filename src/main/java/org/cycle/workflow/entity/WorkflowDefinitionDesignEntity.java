package org.cycle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

@Data
@TableName("WF_DEFINITION_DESIGN")
public class WorkflowDefinitionDesignEntity extends BaseEntity {
    private String definitionId;
    private String designJson;
    private Integer versionNo;
}

