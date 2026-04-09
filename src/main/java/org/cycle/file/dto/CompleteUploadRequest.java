package org.cycle.file.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class CompleteUploadRequest {

    @NotBlank(message = "uploadId不能为空")
    private String uploadId;

    @NotBlank(message = "文件MD5不能为空")
    private String fileMd5;

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    private String contentType;

    @NotNull(message = "总分片不能为空")
    private Integer totalChunks;
}

