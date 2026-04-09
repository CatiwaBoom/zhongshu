package org.cycle.file.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import org.cycle.common.entity.BaseEntity;

import java.io.Serializable;

@Data
@TableName("sys_file_object")
public class FileObjectEntity extends BaseEntity implements Serializable {

    private String fileMd5;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private Long chunkSize;
    private Integer totalChunks;
    private String storagePath;
    private Long storageSize;
    private String encryptAlgorithm;
    private String cipherIv;
    private String wrapIv;
    private String wrappedDek;
    private Integer status;
    private Integer uploadCount;
}

