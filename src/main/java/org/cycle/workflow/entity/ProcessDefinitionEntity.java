package org.cycle.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

/**
 * 流程定义实体（版本化）。
 */
@Data
@TableName("wf_process_def")
public class ProcessDefinitionEntity extends BaseEntity {
    /** 流程编码 */
    private String code;
    /** 流程名称 */
    private String name;
    /** 版本号 */
    private Integer versionNo;
    /** 状态：1-启用，0-停用 */
    private Integer status;
    /** 是否最新版本：1-是，0-否 */
    private Integer isLatest;
    /** 备注 */
    private String remark;
}
