package org.cycle.notification.controller;

import lombok.RequiredArgsConstructor;
import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.notification.dto.NotificationListResponse;
import org.cycle.notification.dto.NotificationSendRequest;
import org.cycle.notification.service.NotificationRealtimeService;
import org.cycle.notification.service.NotificationService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notification")
public class NotificationController extends BaseController {

    private final NotificationService notificationService;
    private final NotificationRealtimeService notificationRealtimeService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("token") String token) {
        String userId = notificationRealtimeService.resolveUserIdByToken(token);
        if (userId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "SSE认证失败");
        }
        long unreadCount = notificationService.countUnread(userId);
        return notificationRealtimeService.createEmitter(userId, unreadCount);
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        String userId = getCurrentUserId();
        long count = notificationService.countUnread(userId);
        Map<String, Long> data = new HashMap<>();
        data.put("count", count);
        return success(data, "查询成功");
    }

    @GetMapping("/list")
    public Result<NotificationListResponse> list(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                 @RequestParam(value = "size", defaultValue = "10") Long size,
                                                 @RequestParam(value = "keyword", required = false) String keyword,
                                                 @RequestParam(value = "isRead", required = false) Integer isRead,
                                                 @RequestParam(value = "bizType", required = false) String bizType) {
        String userId = getCurrentUserId();
        return success(notificationService.listMine(userId, page, size, keyword, isRead, bizType), "查询成功");
    }

    @PostMapping("/read/{id}")
    public Result<Void> markRead(@PathVariable("id") String id) {
        String userId = getCurrentUserId();
        boolean ok = notificationService.markRead(userId, id);
        if (!ok) {
            return fail(404, "消息不存在或已读");
        }
        return success(null, "标记已读成功");
    }

    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        String userId = getCurrentUserId();
        notificationService.markAllRead(userId);
        return success(null, "全部已读成功");
    }

    @PostMapping("/send")
    public Result<Void> send(@Valid @RequestBody NotificationSendRequest request) {
        String userId = getCurrentUserId();
        notificationService.sendToUsers(userId, request);
        return success(null, "发送成功");
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "未登录");
        }
        return authentication.getName();
    }
}
