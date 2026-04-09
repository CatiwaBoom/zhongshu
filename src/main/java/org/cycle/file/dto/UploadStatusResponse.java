package org.cycle.file.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UploadStatusResponse {
    private String uploadId;
    private String fileMd5;
    private Integer totalChunks;
    private Integer uploadedChunksCount;
    private Integer status;
    private String fileObjectId;
    private List<Integer> uploadedChunks = new ArrayList<>();
}

