package org.cycle.menu.controller;

import lombok.RequiredArgsConstructor;
import org.cycle.menu.service.MenuRealtimeService;
import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/menus")
public class MenuController extends BaseController {

    private final MenuRealtimeService menuRealtimeService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("token") String token) {
        String userId = menuRealtimeService.resolveUserIdByToken(token);
        if (userId == null) throw new ResponseStatusException(UNAUTHORIZED, "SSE认证失败");
        return menuRealtimeService.createEmitter(userId);
    }

    /**
     * 手动触发给指定用户的菜单刷新通知（用于管理后台或权限变更后被调用）
     * body: {"userId":"..."} 或 {"userId":null, "broadcast":true}
     */
    @PostMapping("/push")
    public Result<Void> push(@RequestBody Map<String, Object> body) {
        Object uid = body.get("userId");
        boolean broadcast = Boolean.TRUE.equals(body.get("broadcast"));
        if (uid == null && !broadcast) return fail(400, "userId 或 broadcast 必须指定");
        if (broadcast) {
            // 简单实现：如果需要广播，暂时返回成功并请外部通过 role->user 列表逐个调用 push
            return success(null, "请使用 role 规则分发给受影响用户，或逐个 userId 调用此接口（已接收请求）");
        }
        String userId = String.valueOf(uid);
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", "menuUpdate");
        payload.put("ts", System.currentTimeMillis());
        menuRealtimeService.pushMenuUpdate(userId, payload);
        return success(null, "推送成功");
    }
}

