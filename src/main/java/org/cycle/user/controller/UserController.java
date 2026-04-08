package org.cycle.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.user.entity.UserEntity;
import org.cycle.user.mapper.UserMapper;
import org.cycle.user.service.UserService;
import org.cycle.user.dto.UserCreateRequest;
import org.cycle.user.dto.UserUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 用户管理Controller
 */
@RestController
@RequestMapping("/user")
public class UserController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    /**
     * 列表（分页 + 过滤）
     */
    @GetMapping("/list")
    public Result<Page<UserEntity>> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "isSuper", required = false) Integer isSuper,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size
    ) {
        try {
            QueryWrapper<UserEntity> qw = new QueryWrapper<>();
            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = keyword.trim();
                qw.and(w -> w.like("username", kw)
                        .or().like("display_name", kw)
                        .or().like("email", kw)
                        .or().like("mobile", kw));
            }
            if (status != null) qw.eq("status", status);
            if (isSuper != null) qw.eq("is_super", isSuper);

            Page<UserEntity> p = new Page<>(Math.max(1, page), Math.max(1, size));
            Page<UserEntity> result = userService.page(p, qw);
            return success(result);
        } catch (Exception e) {
            log.error("查询用户列表失败", e);
            return fail(500, "查询用户列表失败：" + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public Result<UserEntity> getById(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) return fail(400, "用户ID不能为空");
        UserEntity u = userService.getById(id);
        if (u == null) return fail(404, "用户不存在");
        return success(u);
    }

    @PostMapping("/add")
    public Result<Void> add(@RequestBody UserCreateRequest req) {
        try {
            // username unique check
            QueryWrapper<UserEntity> qw = new QueryWrapper<>();
            qw.eq("username", req.getUsername());
            Long cnt = userMapper.selectCount(qw);
            if (cnt != null && cnt > 0) return fail(409, "用户名已存在");

            if (req.getEmail() != null && !req.getEmail().trim().isEmpty()) {
                QueryWrapper<UserEntity> q2 = new QueryWrapper<>();
                q2.eq("email", req.getEmail());
                Long c2 = userMapper.selectCount(q2);
                if (c2 != null && c2 > 0) return fail(409, "邮箱已被占用");
            }

            UserEntity e = new UserEntity();
            BeanUtils.copyProperties(req, e);
            boolean ok = userService.save(e);
            if (ok) return success(null, "新增用户成功");
            else return fail(5002, "新增用户失败");
        } catch (Exception ex) {
            log.error("新增用户失败", ex);
            return fail(500, "新增用户失败：" + ex.getMessage());
        }
    }

    @PostMapping("/update/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody UserUpdateRequest req) {
        if (id == null || id.trim().isEmpty()) return fail(400, "用户ID不能为空");
        try {
            // uniqueness checks if username/email provided
            if (req.getUsername() != null && !req.getUsername().trim().isEmpty()) {
                QueryWrapper<UserEntity> qw = new QueryWrapper<>();
                qw.eq("username", req.getUsername()).ne("id", id);
                Long cnt = userMapper.selectCount(qw);
                if (cnt != null && cnt > 0) return fail(409, "用户名已存在");
            }
            if (req.getEmail() != null && !req.getEmail().trim().isEmpty()) {
                QueryWrapper<UserEntity> qw = new QueryWrapper<>();
                qw.eq("email", req.getEmail()).ne("id", id);
                Long cnt = userMapper.selectCount(qw);
                if (cnt != null && cnt > 0) return fail(409, "邮箱已被占用");
            }

            UserEntity e = new UserEntity();
            BeanUtils.copyProperties(req, e);
            e.setId(id);
            // if password empty string treat as null so service will skip updating
            if (e.getPassword() != null && e.getPassword().trim().isEmpty()) e.setPassword(null);

            boolean ok = userService.updateById(e);
            if (ok) return success(null, "用户修改成功");
            else return fail(5003, "用户修改失败");
        } catch (Exception ex) {
            log.error("修改用户失败", ex);
            return fail(500, "修改用户失败：" + ex.getMessage());
        }
    }

    @GetMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) return fail(400, "用户ID不能为空");
        try {
            boolean ok = userService.removeById(id);
            if (ok) return success(null, "用户删除成功");
            else return fail(5004, "用户删除失败");
        } catch (Exception ex) {
            log.error("删除用户失败", ex);
            return fail(500, "删除用户失败：" + ex.getMessage());
        }
    }
}


