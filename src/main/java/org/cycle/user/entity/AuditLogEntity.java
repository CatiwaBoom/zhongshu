package org.cycle.user.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.io.Serializable;

/**
 * 审计日志实体 sys_audit_log
 * 用于记录登录、登出、踢人等关键安全操作
 */
@Data
@TableName("sys_audit_log")
public class AuditLogEntity extends BaseEntity implements Serializable {
    /** 操作关联的用户ID（可为空，例如匿名操作） */
    private String userId;

    /** 操作类型，例如 LOGIN, LOGOUT, KICK */
    private String action;

    /** 详细信息或原因 */
    private String detail;

    /** 操作发起 IP */
    private String ip;
}


