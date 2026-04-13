package org.cycle.model.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 业务模型字段 DTO，用于前后端交互
 */
@Data
public class BusinessFieldDto {
    /** 字段ID（编辑时使用） */
    private String id;

    /** 字段显示名称，如：用户名 */
    @NotBlank(message = "字段显示名称不能为空")
    private String fieldName;

    /** 物理列名，如：username */
    @NotBlank(message = "列名不能为空")
    private String columnName;

    /** 数据类型，例如：VARCHAR、INT、DECIMAL、DATE、DATETIME、BOOLEAN、TEXT */
    @NotBlank(message = "数据类型不能为空")
    private String dataType;

    /** 长度或精度（可选） */
    private Integer length;

    /** 是否为主键：true/false */
    @NotNull
    private Boolean isPrimary;

    /** 是否可空：true=可空，false=不可空 */
    @NotNull
    private Boolean isNullable;

    /** 字段注释 */
    private String fieldComment;

    /** 字段默认值（文本形式） */
    private String defaultValue;

    /** 字段是否唯一：true=唯一，false=非唯一 */
    private Boolean isUnique;

    /** 字段是否建索引：true=建索引，false=不建索引 */
    private Boolean isIndexed;

    /** 排序值（可选） */
    private Integer sortOrder;
}

