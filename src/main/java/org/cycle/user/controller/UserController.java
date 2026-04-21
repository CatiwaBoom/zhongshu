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
import org.cycle.user.dto.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.google.common.util.concurrent.RateLimiter;

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

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Autowired
    private RateLimiter userQueryRateLimiter;

    /**
     * 列表（分页 + 过滤）
     */
    @GetMapping("/list")
    @Cacheable(value = "userList", 
               key = "#keyword + '_' + #status + '_' + #isSuper + '_' + #page + '_' + #size",
               unless = "#result == null || #result.code != 200")
    public Result<?> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "isSuper", required = false) Integer isSuper,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "10") Integer size,
            @RequestParam(value = "compress", defaultValue = "false") Boolean compress
    ) {
        // 限流检查
        if (!userQueryRateLimiter.tryAcquire()) {
            return fail(429, "请求过于频繁，请稍后重试");
        }
        
        long startTime = System.currentTimeMillis();
        try {
            QueryWrapper<UserEntity> qw = new QueryWrapper<>();
            // 只查询必要字段
            qw.select("id", "username", "display_name", "email", "mobile", "status", "is_super", "created_at");
            
            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = keyword.trim();
                // 使用likeRight替代like，利用索引提高查询性能
                qw.and(w -> w.likeRight("username", kw)
                        .or().likeRight("display_name", kw)
                        .or().likeRight("email", kw)
                        .or().likeRight("mobile", kw));
            }
            if (status != null) qw.eq("status", status);
            if (isSuper != null) qw.eq("is_super", isSuper);

            // 限制最大页面大小，防止前端请求过大的数据量
            int maxSize = 100;
            int actualSize = Math.min(Math.max(1, size), maxSize);
            Page<UserEntity> p = new Page<>(Math.max(1, page), actualSize);
            Page<UserEntity> result = userService.page(p, qw);
            
            // 转换为UserDTO，减少数据传输量
            Page<UserDTO> dtoPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
            List<UserDTO> dtoList = result.getRecords().stream().map(user -> {
                UserDTO dto = new UserDTO();
                BeanUtils.copyProperties(user, dto);
                return dto;
            }).collect(java.util.stream.Collectors.toList());
            dtoPage.setRecords(dtoList);
            
            // 根据compress参数决定是否返回压缩的数据结构
            if (compress) {
                // 构建压缩的数据结构
                Map<String, Object> compressed = new HashMap<>();
                compressed.put("total", dtoPage.getTotal());
                compressed.put("current", dtoPage.getCurrent());
                compressed.put("size", dtoPage.getSize());
                compressed.put("pages", dtoPage.getPages());
                compressed.put("data", dtoList);
                return success(compressed);
            }
            
            return success(dtoPage);
        } catch (Exception e) {
            log.error("查询用户列表失败", e);
            return fail(500, "查询用户列表失败：" + e.getMessage());
        } finally {
            long endTime = System.currentTimeMillis();
            long costTime = endTime - startTime;
            // 记录慢查询
            if (costTime > 500) {
                log.warn("Slow query: user/list, cost: {}ms, params: keyword={}, status={}, isSuper={}, page={}, size={}",
                        costTime, keyword, status, isSuper, page, size);
            }
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

    /**
     * 游标分页查询
     * 使用游标分页替代传统的LIMIT OFFSET分页，适用于大数据量查询
     */
    @GetMapping("/list/cursor")
    public Result<Map<String, Object>> listByCursor(
            @RequestParam(value = "lastId", required = false) String lastId,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "isSuper", required = false) Integer isSuper,
            @RequestParam(value = "size", defaultValue = "10") Integer size
    ) {
        try {
            QueryWrapper<UserEntity> qw = new QueryWrapper<>();
            // 只查询必要字段
            qw.select("id", "username", "display_name", "email", "mobile", "status", "is_super", "created_at");
            
            // 游标分页条件
            if (lastId != null && !lastId.trim().isEmpty()) {
                qw.gt("id", lastId);
            }
            
            if (keyword != null && !keyword.trim().isEmpty()) {
                String kw = keyword.trim();
                // 使用likeRight替代like，利用索引提高查询性能
                qw.and(w -> w.likeRight("username", kw)
                        .or().likeRight("display_name", kw)
                        .or().likeRight("email", kw)
                        .or().likeRight("mobile", kw));
            }
            if (status != null) qw.eq("status", status);
            if (isSuper != null) qw.eq("is_super", isSuper);
            
            // 限制最大页面大小，防止返回过多数据
            int maxSize = 50;
            int actualSize = Math.min(Math.max(1, size), maxSize);
            // 按ID排序，确保游标分页的稳定性
            qw.orderByAsc("id").last("LIMIT " + actualSize);
            
            List<UserEntity> list = userService.list(qw);
            
            // 转换为UserDTO，减少数据传输量
            List<UserDTO> dtoList = list.stream().map(user -> {
                UserDTO dto = new UserDTO();
                BeanUtils.copyProperties(user, dto);
                return dto;
            }).collect(java.util.stream.Collectors.toList());
            
            // 构建响应结果
            Map<String, Object> result = new HashMap<>();
            result.put("data", dtoList);
            
            // 返回下一页的游标信息
            if (!list.isEmpty()) {
                UserEntity last = list.get(list.size() - 1);
                Map<String, String> cursor = new HashMap<>();
                cursor.put("lastId", last.getId());
                result.put("nextCursor", cursor);
            }
            
            return success(result, "查询成功");
        } catch (Exception e) {
            log.error("游标分页查询失败", e);
            return fail(500, "查询失败：" + e.getMessage());
        }
    }

    /**
     * 批量ID查询
     */
    @PostMapping("/list/by-ids")
    public Result<List<UserDTO>> listByIds(@RequestBody List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return success(Collections.emptyList());
        }
        
        // 限制批量查询的ID数量，防止数据量过大
        int maxIds = 100;
        if (ids.size() > maxIds) {
            return fail(400, "批量查询的ID数量不能超过" + maxIds + "个");
        }
        
        try {
            // 只查询必要字段
            QueryWrapper<UserEntity> qw = new QueryWrapper<>();
            qw.select("id", "username", "display_name", "email", "mobile", "status", "is_super");
            qw.in("id", ids);
            
            List<UserEntity> list = userService.list(qw);
            
            // 转换为UserDTO，减少数据传输量
            List<UserDTO> dtoList = list.stream().map(user -> {
                UserDTO dto = new UserDTO();
                BeanUtils.copyProperties(user, dto);
                return dto;
            }).collect(java.util.stream.Collectors.toList());
            
            return success(dtoList, "查询成功");
        } catch (Exception e) {
            log.error("批量查询失败", e);
            return fail(500, "查询失败：" + e.getMessage());
        }
    }

    /**
     * 异步查询接口
     * 适用于大数据量查询，返回任务ID，通过任务ID获取查询结果
     */
    @GetMapping("/list/async")
    public Result<String> listAsync(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) Integer status,
            @RequestParam(value = "isSuper", required = false) Integer isSuper,
            @RequestParam(value = "size", defaultValue = "1000") Integer size
    ) {
        try {
            // 生成查询任务ID
            String taskId = UUID.randomUUID().toString();
            
            // 异步执行查询
            CompletableFuture.supplyAsync(() -> {
                QueryWrapper<UserEntity> qw = new QueryWrapper<>();
                // 只查询必要字段
                qw.select("id", "username", "display_name", "email", "mobile", "status", "is_super", "created_at");
                
                if (keyword != null && !keyword.trim().isEmpty()) {
                    String kw = keyword.trim();
                    // 使用likeRight替代like，利用索引提高查询性能
                    qw.and(w -> w.likeRight("username", kw)
                            .or().likeRight("display_name", kw)
                            .or().likeRight("email", kw)
                            .or().likeRight("mobile", kw));
                }
                if (status != null) qw.eq("status", status);
                if (isSuper != null) qw.eq("is_super", isSuper);
                
                // 限制最大查询数量，防止数据量过大
                int maxSize = 1000;
                int actualSize = Math.min(Math.max(1, size), maxSize);
                qw.orderByAsc("id").last("LIMIT " + actualSize);
                
                List<UserEntity> list = userService.list(qw);
                
                // 转换为UserDTO，减少数据传输量
                return list.stream().map(user -> {
                    UserDTO dto = new UserDTO();
                    BeanUtils.copyProperties(user, dto);
                    return dto;
                }).collect(java.util.stream.Collectors.toList());
            }, taskExecutor).thenAccept(dtoList -> {
                // 将结果存储到Redis，设置过期时间10分钟
                redisTemplate.opsForValue().set("user_query_result:" + taskId, dtoList, 10, java.util.concurrent.TimeUnit.MINUTES);
            });
            
            return success(taskId, "查询任务已提交，请使用taskId获取结果");
        } catch (Exception e) {
            log.error("异步查询失败", e);
            return fail(500, "查询失败：" + e.getMessage());
        }
    }

    /**
     * 获取异步查询结果
     */
    @GetMapping("/query/result/{taskId}")
    public Result<Object> getQueryResult(@PathVariable String taskId) {
        try {
            Object result = redisTemplate.opsForValue().get("user_query_result:" + taskId);
            if (result == null) {
                return fail(404, "查询结果不存在或已过期");
            }
            return success(result, "查询成功");
        } catch (Exception e) {
            log.error("获取查询结果失败", e);
            return fail(500, "查询失败：" + e.getMessage());
        }
    }
}


