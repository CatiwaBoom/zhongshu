package org.cycle.dataSource.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cycle.dataSource.entity.DataSourceEntity;

@Mapper
public interface DataSourceMapper extends BaseMapper<DataSourceEntity> {

}