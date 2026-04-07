package org.cycle.user.service;

import org.cycle.user.entity.UserEntity;

public interface AuthService {
    UserEntity authenticate(String username, String password);
}

