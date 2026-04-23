package org.cycle.file.service;

import org.cycle.file.dto.CompleteUploadRequest;
import org.cycle.file.dto.FileObjectVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.cycle.file.dto.InitUploadRequest;
import org.cycle.file.dto.InitUploadResponse;
import org.cycle.file.dto.UploadStatusResponse;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

public interface FilePlatformService {

    InitUploadResponse initUpload(InitUploadRequest request, String userId);

    void uploadChunk(String uploadId,
                     String fileMd5,
                     Integer chunkIndex,
                     Integer totalChunks,
                     MultipartFile chunkFile,
                     String userId) throws IOException;

    FileObjectVO completeUpload(CompleteUploadRequest request, String userId) throws IOException;

    UploadStatusResponse getStatus(String uploadId);

    /**
     * 列表（支持分页与关键字过滤）
     */
    Page<FileObjectVO> listObjects(String keyword, Integer page, Integer size);

    void downloadDecrypted(String fileId, HttpServletResponse response) throws IOException;

    /**
     * 物理删除文件对象（删除存储文件并删除数据库记录）
     */
    void deleteFile(String fileId, String userId) throws IOException;
}
