package org.cycle.notification.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class NotificationListResponse {

    private Long total;

    private Long current;

    private Long size;

    private List<NotificationItemVO> records = Collections.emptyList();
}

