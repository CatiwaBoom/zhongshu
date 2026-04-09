package org.cycle.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.io.Serializable;
import java.sql.Timestamp;

@Data
@TableName("sys_file_upload_session")
public class FileUploadSessionEntity extends BaseEntity implements Serializable {

    private String uploadId;
    private String fileMd5;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private Long chunkSize;
    private Integer totalChunks;
    private Integer uploadedChunks;
    private Integer status;
    private String fileObjectId;
    private Timestamp lastChunkAt;
}

