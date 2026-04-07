package org.cycle.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.cycle.user.entity.UserEntity;
import org.cycle.user.mapper.UserMapper;
import org.cycle.user.service.UserService;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements UserService {

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    @Override
    public boolean save(UserEntity entity) {
        if (entity.getPassword() != null && !entity.getPassword().isEmpty()) {
            entity.setPassword(passwordEncoder.encode(entity.getPassword()));
        }
        return super.save(entity);
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
        return super.updateById(entity);
    }
}

