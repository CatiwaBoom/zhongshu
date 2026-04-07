package org.cycle.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.io.Serializable;

/**
 * 权限实体映射到表 sys_permission
 */
@Data
@TableName("sys_permission")
public class PermissionEntity extends BaseEntity implements Serializable {
    /** 权限名称 */
    private String name;

    /** 资源标识（例如接口路径或资源名） */
    private String resource;

    /** 动作（例如 GET/POST 或自定义动作） */
    private String action;
}


