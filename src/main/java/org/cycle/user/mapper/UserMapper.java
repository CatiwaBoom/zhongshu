package org.cycle.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.CacheNamespace;
import org.apache.ibatis.annotations.Mapper;
import org.cycle.user.entity.UserEntity;

@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

}

