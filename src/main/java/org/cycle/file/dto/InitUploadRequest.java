package org.cycle.file.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class InitUploadRequest {

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    private String contentType;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件大小必须大于0")
    private Long fileSize;

    @NotBlank(message = "文件MD5不能为空")
    private String fileMd5;

    @NotNull(message = "分片大小不能为空")
    @Min(value = 1024 * 1024, message = "分片大小至少1MB")
    @Max(value = 50L * 1024 * 1024, message = "分片大小最多50MB")
    private Long chunkSize;

    @NotNull(message = "分片总数不能为空")
    @Min(value = 1, message = "分片总数必须大于0")
    private Integer totalChunks;
}

