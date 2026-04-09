package org.cycle.file.dto;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class FileObjectVO {
    private String id;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String fileMd5;
    private Integer totalChunks;
    private Integer uploadCount;
    private Timestamp createdAt;
}

