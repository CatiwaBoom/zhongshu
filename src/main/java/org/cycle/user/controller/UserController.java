package org.cycle.user.controller;

import lombok.extern.slf4j.Slf4j;
import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户管理Controller
 * 继承BaseController，复用统一响应格式
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController extends BaseController {

    /**
     * 用户登录
     * @param params 登录参数
     * @return 统一格式响应
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, Object> params) {
        if (params == null) {
            return fail(400, "请求参数不能为空");
        }

        Object usernameObj = params.get("username");
        Object passwordObj = params.get("password");

        String username = usernameObj == null ? "" : usernameObj.toString().trim();
        String password = passwordObj == null ? "" : passwordObj.toString().trim();

        if (username.isEmpty()) {
            return fail(400, "用户名不能为空");
        }
        if (password.isEmpty()) {
            return fail(400, "密码不能为空");
        }

        try {
            // 这里先做演示登录，后续可替换为UserService鉴权
            if ("admin".equals(username) && "123456".equals(password)) {
                Map<String, Object> data = new HashMap<>();
                data.put("token", "fake-token-123456");
                data.put("username", username);
                return success(data, "登录成功");
            } else {
                return fail(401, "用户名或密码错误");
            }
        } catch (Exception e) {
            log.error("用户登录失败", e);
            return fail(500, "用户登录失败：" + e.getMessage());
        }
    }

    /**
     * 用户退出登录
     * @return 统一格式响应
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        try {
            return success(null, "退出登录成功");
        } catch (Exception e) {
            log.error("退出登录失败", e);
            return fail(500, "退出登录失败：" + e.getMessage());
        }
    }
}