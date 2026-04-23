package org.cycle.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.io.Serializable;

@Data
@TableName("sys_role_menu")
public class RoleMenuEntity extends BaseEntity implements Serializable {
    private String roleId;
    private String menuId;
}

