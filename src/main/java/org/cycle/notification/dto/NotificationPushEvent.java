package org.cycle.notification.dto;

import lombok.Data;

import java.io.Serializable;
import java.sql.Timestamp;

@Data
public class NotificationPushEvent implements Serializable {

    private String userId;

    private String messageId;

    private String title;

    private String content;

    private String bizType;

    private String bizId;

    private Timestamp createdAt;

    private Long unreadCount;
}

