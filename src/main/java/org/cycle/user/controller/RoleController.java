package org.cycle.user.controller;

import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.user.entity.RoleEntity;
import org.cycle.user.mapper.RoleMapper;
import org.cycle.user.service.MenuRealtimeService;
import org.cycle.user.service.RoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.cycle.user.mapper.RoleMenuMapper;
import org.cycle.user.entity.RoleMenuEntity;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

@RestController
@RequestMapping("/roles")
public class RoleController extends BaseController {

    @Resource
    private RoleService roleService;

    @Resource
    private RoleMenuMapper roleMenuMapper;

    @Resource
    private RoleMapper roleMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private MenuRealtimeService menuRealtimeService;

    @GetMapping("")
    public Result<List<RoleEntity>> list() {
        List<RoleEntity> list = roleService.list();
        return success(list);
    }

    @GetMapping("/{id}")
    public Result<RoleEntity> getById(@PathVariable String id) {
        RoleEntity r = roleService.getById(id);
        if (r == null) return fail(404, "角色不存在");
        return success(r);
    }

    @PostMapping("")
    public Result<Void> create(@RequestBody RoleEntity req) {
        try {
            if (req.getId() == null || req.getId().trim().isEmpty()) req.setId(UUID.randomUUID().toString());
            
            if (req.getCode() == null || req.getCode().trim().isEmpty()) {
                String name = req.getName();
                if (name != null && !name.trim().isEmpty()) {
                    String code = name.trim().toUpperCase().replaceAll("\\s+", "_");
                    req.setCode(code);
                } else {
                    req.setCode(UUID.randomUUID().toString());
                }
            }
            boolean ok = roleService.save(req);
            if (ok) return success(null, "创建成功");
            else return fail(500, "创建失败");
        } catch (Exception e) {
            return fail(500, "创建失败：" + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody RoleEntity req) {
        try {
            req.setId(id);
            boolean ok = roleService.updateById(req);
            if (ok) return success(null, "更新成功");
            else return fail(500, "更新失败");
        } catch (Exception e) {
            return fail(500, "更新失败：" + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        try {
            boolean ok = roleService.removeById(id);
            if (ok) return success(null, "删除成功");
            else return fail(500, "删除失败");
        } catch (Exception e) {
            return fail(500, "删除失败：" + e.getMessage());
        }
    }

    @GetMapping("/{id}/menus")
    public Result<List<String>> getRoleMenus(@PathVariable String id) {
        // return list of menu ids assigned to role
        List<RoleMenuEntity> list = roleMenuMapper.selectList(null);
        List<String> menuIds = new ArrayList<>();
        for (RoleMenuEntity r : list) {
            if (id.equals(r.getRoleId())) menuIds.add(r.getMenuId());
        }
        return success(menuIds);
    }

    // 获取某角色已分配的用户 id 列表
    @GetMapping("/{id}/users")
    public Result<List<String>> getRoleUsers(@PathVariable String id) {
        try {
            List<String> userIds = roleMapper.selectUserIdsByRoleId(id);
            return success(userIds == null ? new ArrayList<>() : userIds);
        } catch (Exception ex) {
            return fail(500, "查询失败：" + ex.getMessage());
        }
    }

    public static class AssignUsersRequest {
        public List<String> userIds;
    }

    // 为角色分配用户
    @PostMapping("/{id}/users")
    public Result<Void> assignUsersToRole(@PathVariable String id, @RequestBody AssignUsersRequest req) {
        try {
            // 读取变更前的用户列表（以便同时通知被移除和新增的用户）
            List<String> beforeUserIds;
            try {
                beforeUserIds = roleMapper.selectUserIdsByRoleId(id);
                if (beforeUserIds == null) beforeUserIds = new ArrayList<>();
            } catch (Exception ignored) { beforeUserIds = new ArrayList<>(); }

            // 删除已有关联并插入新的关联
            roleMapper.deleteUserRolesByRoleId(id);
            if (req != null && req.userIds != null && !req.userIds.isEmpty()) {
                for (String uid : req.userIds) {
                    String rid = UUID.randomUUID().toString();
                    roleMapper.insertUserRole(rid, uid, id);
                }
            }

            // 计算受影响的用户集合（并去重）
            Set<String> affected = new HashSet<>();
            if (beforeUserIds != null) affected.addAll(beforeUserIds);
            if (req != null && req.userIds != null) affected.addAll(req.userIds);

            // 清理受影响用户的角色缓存并推送菜单更新事件
            try {
                if (redisTemplate != null) {
                    for (String uid : affected) {
                        try { redisTemplate.delete("user:roles:" + uid); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            try {
                if (menuRealtimeService != null) {
                    for (String uid : affected) {
                        try {
                            Map<String, Object> payload = new HashMap<>();
                            payload.put("type", "roleAssignment");
                            payload.put("roleId", id);
                            payload.put("ts", System.currentTimeMillis());
                            menuRealtimeService.pushMenuUpdate(uid, payload);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}

            return success(null, "分配成功");
        } catch (Exception ex) {
            return fail(500, "分配失败：" + ex.getMessage());
        }
    }

    public static class RoleMenuRequest {
        public List<String> menuIds;
    }

    @PostMapping("/{id}/menus")
    public Result<Void> assignMenusToRole(@PathVariable String id, @RequestBody RoleMenuRequest req) {
        try {
            // 删除已有
            roleMenuMapper.deleteByRoleId(id);
            if (req.menuIds != null && !req.menuIds.isEmpty()) {
                for (String mid : req.menuIds) {
                    RoleMenuEntity e = new RoleMenuEntity();
                    e.setId(UUID.randomUUID().toString());
                    e.setRoleId(id);
                    e.setMenuId(mid);
                    roleMenuMapper.insertOne(e);
                }
            }
            // 清理受影响用户的角色缓存（如果使用了 Redis 缓存 user:roles:{userId}）
            try {
                if (redisTemplate != null && roleMapper != null) {
                    List<String> userIds = roleMapper.selectUserIdsByRoleId(id);
                    if (userIds != null) {
                        for (String uid : userIds) {
                            try { redisTemplate.delete("user:roles:" + uid); } catch (Exception ignored) {}
                            try { redisTemplate.delete("user:roleIds:" + uid); } catch (Exception ignored) {}
                        }
                        // 增加一个 menusVersion key，前端可以轮询该版本号以感知权限变化
                        for (String uid : userIds) {
                            // 通过 SSE 主动通知在线用户刷新菜单（移除了 menusVersion 的 Redis 自增，改用 SSE）
                            try {
                                if (menuRealtimeService != null) {
                                    Map<String, Object> payload = new HashMap<>();
                                    payload.put("type", "roleMenuChange");
                                    payload.put("roleId", id);
                                    payload.put("ts", System.currentTimeMillis());
                                    menuRealtimeService.pushMenuUpdate(uid, payload);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
            return success(null, "分配成功");
        } catch (Exception ex) {
            return fail(500, "分配失败：" + ex.getMessage());
        }
    }
}




