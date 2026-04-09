package org.cycle.file.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InitUploadResponse {
    private String uploadId;
    private Boolean instantUpload;
    private String fileId;
    private List<Integer> uploadedChunks = new ArrayList<>();
}

