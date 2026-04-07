package org.cycle.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.io.Serializable;

/**
 * 角色实体映射到表 sys_role
 */
@Data
@TableName("sys_role")
public class RoleEntity extends BaseEntity implements Serializable {
    /** 角色名称 */
    private String name;

    /** 角色编码（用于权限检查） */
    private String code;

    /** 角色描述 */
    private String description;
}


