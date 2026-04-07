package org.cycle.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.io.Serializable;

@Data
@TableName("sys_user")
public class UserEntity extends BaseEntity implements Serializable {
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     */
    private String password;
    /**
     * 盐值
     */
    private String salt;
    /**
     * 展示名称
     */
    private String displayName;
    /**
     * 邮箱
     */
    private String email;
    /**
     * 手机号
     */
    private String mobile;
    /**
     * 状态 1 enabled, 0 disabled
     */
    private Integer status;
    /**
     * 是否是超级管理员  1 super admin
     */
    private Integer isSuper;
}

