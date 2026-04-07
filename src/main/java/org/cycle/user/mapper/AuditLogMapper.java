package org.cycle.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cycle.user.entity.AuditLogEntity;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {

}

