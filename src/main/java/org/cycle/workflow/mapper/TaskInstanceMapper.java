package org.cycle.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cycle.workflow.entity.TaskInstanceEntity;

@Mapper
public interface TaskInstanceMapper extends BaseMapper<TaskInstanceEntity> {
}

