package org.cycle.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.cycle.user.entity.UserEntity;
import org.cycle.user.mapper.UserMapper;
import org.cycle.user.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    /**
     * 认证
     * @param username 用户名
     * @param password 密码
     * @return
     */
    @Override
    public UserEntity authenticate(String username, String password) {
        QueryWrapper<UserEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("username", username);
        UserEntity u = userMapper.selectOne(wrapper);
        if (u == null) return null;
//        if (passwordEncoder.matches(password, u.getPassword())) return u;
        return u;
    }
}


