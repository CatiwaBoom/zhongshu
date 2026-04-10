package org.cycle.file.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cycle.common.controller.BaseController;
import org.cycle.common.controller.Result;
import org.cycle.file.dto.CompleteUploadRequest;
import org.cycle.file.dto.FileObjectVO;
import org.cycle.file.dto.InitUploadRequest;
import org.cycle.file.dto.InitUploadResponse;
import org.cycle.file.dto.UploadStatusResponse;
import org.cycle.file.service.FilePlatformService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/file/platform")
public class FilePlatformController extends BaseController {

    private final FilePlatformService filePlatformService;

    @PostMapping("/init")
    public Result<InitUploadResponse> init(@Valid @RequestBody InitUploadRequest request) {
        return success(filePlatformService.initUpload(request, currentUserId()), "初始化上传成功");
    }

    @PostMapping("/chunk")
    public Result<Void> uploadChunk(@RequestParam("uploadId") String uploadId,
                                    @RequestParam("md5") String fileMd5,
                                    @RequestParam("chunkIndex") Integer chunkIndex,
                                    @RequestParam("totalChunks") Integer totalChunks,
                                    @RequestParam("file") MultipartFile file) throws IOException {
        filePlatformService.uploadChunk(uploadId, fileMd5, chunkIndex, totalChunks, file, currentUserId());
        return success(null, "分片上传成功");
    }

    @PostMapping("/complete")
    public Result<FileObjectVO> complete(@Valid @RequestBody CompleteUploadRequest request) throws IOException {
        return success(filePlatformService.completeUpload(request, currentUserId()), "文件上传完成");
    }

    @PostMapping("/merge")
    public Result<FileObjectVO> merge(@RequestParam("uploadId") String uploadId,
                                      @RequestParam("md5") String fileMd5,
                                      @RequestParam("fileName") String fileName,
                                      @RequestParam("totalChunks") Integer totalChunks,
                                      @RequestParam(value = "contentType", required = false) String contentType) throws IOException {
        CompleteUploadRequest request = new CompleteUploadRequest();
        request.setUploadId(uploadId);
        request.setFileMd5(fileMd5);
        request.setFileName(fileName);
        request.setTotalChunks(totalChunks);
        request.setContentType(contentType);
        return success(filePlatformService.completeUpload(request, currentUserId()), "文件合并完成");
    }

    @GetMapping("/upload/{uploadId}/status")
    public Result<UploadStatusResponse> status(@PathVariable("uploadId") String uploadId) {
        return success(filePlatformService.getStatus(uploadId), "查询成功");
    }

    @GetMapping("/list")
    public Result<?> list(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "size", defaultValue = "50") Integer size,
            @RequestParam(value = "compress", defaultValue = "false") Boolean compress
    ) {
        try {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileObjectVO> result = filePlatformService.listObjects(keyword, page, size);

            if (compress != null && compress) {
                java.util.Map<String, Object> compressed = new java.util.HashMap<>();
                compressed.put("total", result.getTotal());
                compressed.put("current", result.getCurrent());
                compressed.put("size", result.getSize());
                compressed.put("pages", result.getPages());
                compressed.put("data", result.getRecords());
                return success(compressed, "查询成功");
            }

            return success(result, "查询成功");
        } catch (Exception e) {
            log.error("查询文件列表失败", e);
            return fail(500, "查询文件列表失败：" + e.getMessage());
        }
    }

    @GetMapping("/download/{fileId}")
    public void download(@PathVariable("fileId") String fileId, HttpServletResponse response) throws IOException {
        filePlatformService.downloadDecrypted(fileId, response);
    }

    @PostMapping("/delete/{fileId}")
    public Result<Void> delete(@PathVariable("fileId") String fileId) throws IOException {
        filePlatformService.deleteFile(fileId, currentUserId());
        return success(null, "删除成功");
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }
        return authentication.getName();
    }
}

