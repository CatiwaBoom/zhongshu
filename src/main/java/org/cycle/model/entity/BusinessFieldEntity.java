package org.cycle.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.baomidou.mybatisplus.annotation.TableField;
import org.cycle.common.entity.BaseEntity;

/**
 * 业务模型字段实体 - 映射表 BUSINESS_FIELD
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("BUSINESS_FIELD")
public class BusinessFieldEntity extends BaseEntity {
    /** 所属模型ID，关联 BUSINESS_MODEL.ID */
    private String modelId;

    /** 字段显示名称，如：用户名 */
    private String fieldName;

    /** 物理列名，如：username */
    private String columnName;

    /** 数据类型，如：VARCHAR、INT、DECIMAL、DATE、DATETIME、BOOLEAN、TEXT */
    private String dataType;

    /** 长度或精度（依据数据类型含义） */
    private Integer length;

    /** 是否为主键：1=是，0=否 */
    private Integer isPrimary;

    /** 是否可空：1=可空，0=不可空 */
    private Integer isNullable;

    /** 字段注释/备注 */
    @TableField("FIELD_COMMENT")
    private String fieldComment;

    /** 字段默认值（文本形式） */
    private String defaultValue;

    /** 字段是否唯一：1=唯一，0=非唯一 */
    private Integer isUnique;

    /** 字段是否建索引：1=建索引，0=不建索引 */
    private Integer isIndexed;

    /** 字段排序，数值越小越靠前 */
    private Integer sortOrder;
}

