package org.cycle.notification.dto;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class NotificationItemVO {

    private String id;

    private String title;

    private String content;

    private String bizType;

    private String bizId;

    private Integer isRead;

    private Timestamp readAt;

    private Timestamp createdAt;
}

