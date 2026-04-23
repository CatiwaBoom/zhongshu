package org.cycle.config;

import org.cycle.user.dto.UserDTO;
import org.cycle.user.entity.UserEntity;
import org.cycle.user.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 缓存预热组件
 * 在应用启动时预加载常用的查询结果到缓存中
 */
@Slf4j
@Component
public class CacheWarmer implements ApplicationRunner {

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void run(org.springframework.boot.ApplicationArguments args) throws Exception {
        // 预热用户列表查询
        warmUpUserList();
    }

    /**
     * 预热用户列表查询
     */
    private void warmUpUserList() {
        try {
            // 构建查询条件
            QueryWrapper<UserEntity> qw = new QueryWrapper<>();
            qw.select("id", "username", "display_name", "email", "mobile", "status", "is_super", "created_at");
            
            // 预热第一页数据
            Page<UserEntity> page = userService.page(new Page<>(1, 10), qw);
            
            // 转换为DTO
            List<UserDTO> dtoList = page.getRecords().stream().map(user -> {
                UserDTO dto = new UserDTO();
                BeanUtils.copyProperties(user, dto);
                return dto;
            }).collect(Collectors.toList());
            
            // 构建缓存键
            String cacheKey = "userList:null_null_null_1_10";
            
            // 存储到Redis，设置过期时间15分钟
            redisTemplate.opsForValue().set(cacheKey, dtoList, 15, TimeUnit.MINUTES);
            
            log.info("用户列表缓存预热成功");
        } catch (Exception e) {
            log.error("预热用户列表缓存失败", e);
        }
    }
}
