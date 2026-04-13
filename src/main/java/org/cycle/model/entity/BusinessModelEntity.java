package org.cycle.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.cycle.common.entity.BaseEntity;

/**
 * 业务模型实体 - 映射表 BUSINESS_MODEL
 * 所有注释均为中文，便于维护
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("BUSINESS_MODEL")
public class BusinessModelEntity extends BaseEntity {
    /** 模型显示名称，如：用户、订单 */
    private String name;

    /** 物理表名，如：users、orders */
    private String tableName;

    /** 模型描述/备注 */
    private String description;
}

