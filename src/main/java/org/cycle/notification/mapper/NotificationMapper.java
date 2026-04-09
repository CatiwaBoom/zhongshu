package org.cycle.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cycle.notification.entity.NotificationEntity;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {
}

