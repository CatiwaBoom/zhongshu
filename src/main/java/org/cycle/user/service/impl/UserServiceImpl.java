package org.cycle.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.cycle.user.entity.UserEntity;
import org.cycle.user.mapper.UserMapper;
import org.cycle.user.service.UserService;

import java.io.Serializable;
import java.util.Set;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 清除用户列表相关的缓存
     */
    private void clearUserListCache() {
        try {
            Set<String> keys = redisTemplate.keys("userList:*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.debug("清除用户列表缓存成功，共清除 {} 个缓存键", keys.size());
            }
        } catch (Exception e) {
            log.warn("清除用户列表缓存失败", e);
        }
    }

    @Override
    public boolean save(UserEntity entity) {
        if (entity.getPassword() != null && !entity.getPassword().isEmpty()) {
            entity.setPassword(passwordEncoder.encode(entity.getPassword()));
        }
        boolean result = super.save(entity);
        if (result) {
            // 保存成功后清除缓存
            clearUserListCache();
        }
        return result;
    }

    @Override
    public boolean updateById(UserEntity entity) {
        // 如果传入了密码，则需要加密再保存；否则避免覆盖原密码
        if (entity.getPassword() != null && !entity.getPassword().isEmpty()) {
            entity.setPassword(passwordEncoder.encode(entity.getPassword()));
        } else {
            // 保持不修改密码：清理为null 以便 MyBatis-Plus 不更新该字段
            entity.setPassword(null);
        }
        boolean result = super.updateById(entity);
        if (result) {
            // 更新成功后清除缓存
            clearUserListCache();
        }
        return result;
    }

    @Override
    public boolean removeById(Serializable id) {
        boolean result = super.removeById(id);
        if (result) {
            // 删除成功后清除缓存
            clearUserListCache();
        }
        return result;
    }
}

