package org.cycle.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.cycle.file.entity.FileChunkEntity;

@Mapper
public interface FileChunkMapper extends BaseMapper<FileChunkEntity> {
}

