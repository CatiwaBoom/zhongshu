package org.cycle.seatunnel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cycle.seatunnel.entity.DataSyncTaskEntity;

@Mapper
public interface DataSyncTaskMapper extends BaseMapper<DataSyncTaskEntity> {
}
