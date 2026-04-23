package org.cycle.user.controller;

import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.user.service.MenuRealtimeService;
import org.cycle.user.entity.MenuEntity;
import org.cycle.user.service.MenuService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.Resource;
import java.util.*;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

/**
 * 菜单管理接口
 */
@RestController
@RequestMapping("/menus")
public class MenuController extends BaseController {

    @Resource
    private MenuService menuService;
    @Resource
    MenuRealtimeService menuRealtimeService;

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
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "menuUpdate");
        payload.put("ts", System.currentTimeMillis());
        menuRealtimeService.pushMenuUpdate(userId, payload);
        return success(null, "推送成功");
    }

    @GetMapping("/tree")
    public Result<List<MenuEntity>> tree() {
        List<MenuEntity> tree = menuService.listTree();
        return success(tree);
    }

    @GetMapping("/{id}")
    public Result<MenuEntity> getById(@PathVariable String id) {
        MenuEntity m = menuService.getById(id);
        if (m == null) return fail(404, "菜单不存在");
        return success(m);
    }

    @PostMapping("")
    public Result<Void> create(@RequestBody MenuEntity req) {
        try {
            if (req.getId() == null || req.getId().trim().isEmpty()) req.setId(UUID.randomUUID().toString());
            boolean ok = menuService.save(req);
            if (ok) return success(null, "创建成功");
            else return fail(500, "创建失败");
        } catch (Exception e) {
            return fail(500, "创建失败：" + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody MenuEntity req) {
        try {
            req.setId(id);
            boolean ok = menuService.updateById(req);
            if (ok) return success(null, "更新成功");
            else return fail(500, "更新失败");
        } catch (Exception e) {
            return fail(500, "更新失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        try {
            boolean ok = menuService.removeById(id);
            if (ok) return success(null, "删除成功");
            else return fail(500, "删除失败");
        } catch (Exception e) {
            return fail(500, "删除失败：" + e.getMessage());
        }
    }
}

