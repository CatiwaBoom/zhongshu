package org.cycle.model.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
// ...existing code...
import java.util.List;

/**
 * 业务模型 DTO，用于创建/更新请求
 */
@Data
public class BusinessModelDto {
    /** 模型ID（编辑时使用） */
    private String id;

    /** 模型显示名称，如：用户 */
    @NotBlank(message = "模型名称不能为空")
    private String name;

    /** 物理表名，如：users */
    @NotBlank(message = "物理表名不能为空")
    private String tableName;

    /** 模型描述 */
    private String description;

    /** 字段定义列表（至少可以为空，前端可校验至少一个字段） */
    @Valid
    private List<BusinessFieldDto> fields;
}

