package org.cycle.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.cycle.user.service.SessionService;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private SessionService sessionService;

    @PostMapping("/sessions/{sessionId}/kick")
    public ResponseEntity<?> kick(@PathVariable String sessionId) {
        // 管理员踢人：仅允许超级管理员调用（需在权限层校验）
        // 标记会话为 revoked，并可触发通知/审计
        sessionService.revokeSession(sessionId, "admin_kick", null);
        return ResponseEntity.ok().build();
    }
}


