package org.cycle.notification.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.io.Serializable;
import java.sql.Timestamp;

@Data
@TableName("sys_notification")
public class NotificationEntity extends BaseEntity implements Serializable {

    private String userId;

    private String title;

    private String content;

    private String bizType;

    private String bizId;

    @TableField("is_read")
    private Integer isRead;

    private Timestamp readAt;
}

