package org.cycle.notification.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.cycle.notification.dto.NotificationListResponse;
import org.cycle.notification.dto.NotificationSendRequest;
import org.cycle.notification.entity.NotificationEntity;

import java.util.List;

public interface NotificationService extends IService<NotificationEntity> {

    void sendToUsers(String senderId, NotificationSendRequest request);

    NotificationListResponse listMine(String userId, long page, long size);

    long countUnread(String userId);

    boolean markRead(String userId, String notificationId);

    int markAllRead(String userId);

    List<String> resolveTargetUserIds(String senderId, NotificationSendRequest request);
}

