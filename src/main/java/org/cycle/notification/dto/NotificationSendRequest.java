package org.cycle.notification.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class NotificationSendRequest {

    @NotBlank(message = "消息标题不能为空")
    private String title;

    @NotBlank(message = "消息内容不能为空")
    private String content;

    private String bizType;

    private String bizId;

    // 为空时默认发送给当前登录用户
    private List<String> targetUserIds;

    // 仅用于演示或管理员批量通知
    private Boolean sendAll;
}

