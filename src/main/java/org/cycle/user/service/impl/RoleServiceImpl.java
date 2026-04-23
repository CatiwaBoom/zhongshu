package org.cycle.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.cycle.user.entity.RoleEntity;
import org.cycle.user.mapper.RoleMapper;
import org.cycle.user.service.RoleService;
import org.springframework.stereotype.Service;

@Service
public class RoleServiceImpl extends ServiceImpl<RoleMapper, RoleEntity> implements RoleService {

}

