package org.cycle.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.io.Serializable;

@Data
@TableName("sys_file_chunk")
public class FileChunkEntity extends BaseEntity implements Serializable {

    private String uploadId;
    private Integer chunkIndex;
    private Long chunkSize;
    private String chunkMd5;
}

