package org.cycle.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cycle.file.dto.CompleteUploadRequest;
import org.cycle.file.dto.FileObjectVO;
import org.cycle.file.dto.InitUploadRequest;
import org.cycle.file.dto.InitUploadResponse;
import org.cycle.file.dto.UploadStatusResponse;
import org.cycle.file.entity.FileChunkEntity;
import org.cycle.file.entity.FileObjectEntity;
import org.cycle.file.entity.FileUploadSessionEntity;
import org.cycle.file.mapper.FileChunkMapper;
import org.cycle.file.mapper.FileObjectMapper;
import org.cycle.file.mapper.FileUploadSessionMapper;
import org.cycle.file.service.FileCryptoService;
import org.cycle.file.service.storage.StorageService;
import org.cycle.file.service.FilePlatformService;
import org.cycle.file.util.FileHashUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文件存储服务实现（支持分片上传、合并、加密后存储到对象存储如 MinIO、以及解密下载与删除）。
 *
 * 主要职责：
 *  - 管理上传会话（sys_file_upload_session）和分片元数据（sys_file_chunk）
 *  - 接收分片并以流式方式上传到对象存储（不在本地保留分片副本）
 *  - 合并分片（从对象存储逐个下载分片流写入合并临时文件），校验 MD5 后加密并上传最终对象到对象存储
 *  - 在数据库中记录文件对象元信息（sys_file_object）：包括加密算法、cipherIv、wrappedDek、storagePath（minio://...）等
 *  - 提供按 id 下载（解密后直写 HTTP 响应流）和物理删除（删除对象存储内对象并清理 DB）功能
 *
 * 设计要点：
 *  - 分片上传采用完全流式（MultipartFile -> InputStream -> 直接上传到对象存储），同时在上传过程中计算分片 MD5 保存到 DB
 *  - 合并过程在服务器临时目录生成合并文件（merged.tmp），以便做 MD5 校验；合并完成并校验通过后执行加密并上传
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilePlatformServiceImpl implements FilePlatformService {

    private static final int STATUS_INIT = 0;
    private static final int STATUS_UPLOADING = 1;
    private static final int STATUS_COMPLETED = 2;
    private static final int STATUS_FAILED = 3;

    private final FileObjectMapper fileObjectMapper;
    private final FileUploadSessionMapper uploadSessionMapper;
    private final FileChunkMapper fileChunkMapper;
    private final StorageService storageService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InitUploadResponse initUpload(InitUploadRequest request, String userId) {
        // 初始化上传会话：
        // 1) 校验文件 MD5 格式
        // 2) 如果已有相同 MD5 的文件对象，增加上传计数并创建一个已完成的会话（instant upload）返回
        // 3) 否则查找是否已有未完成的会话可续传；否则创建新会话并返回已上传分片索引列表（支持断点续传）

        // 说明：该方法会在数据库中插入或查询 upload session（sys_file_upload_session）表
        String md5 = normalizeMd5(request.getFileMd5());
        validateMd5(md5);

        FileObjectEntity existed = findFileObjectByMd5(md5);
        if (existed != null) {
            incUploadCount(existed, userId);
            FileUploadSessionEntity doneSession = createSession(request, md5, userId);
            doneSession.setStatus(STATUS_COMPLETED);
            doneSession.setUploadedChunks(request.getTotalChunks());
            doneSession.setFileObjectId(existed.getId());
            doneSession.setLastChunkAt(new Timestamp(System.currentTimeMillis()));
            uploadSessionMapper.insert(doneSession);

            InitUploadResponse response = new InitUploadResponse();
            response.setUploadId(doneSession.getUploadId());
            response.setInstantUpload(true);
            response.setFileId(existed.getId());
            response.setUploadedChunks(new ArrayList<>());
            return response;
        }

        FileUploadSessionEntity session = findLatestActiveSession(md5, request.getFileSize(), request.getTotalChunks(), request.getChunkSize());
        if (session == null) {
            session = createSession(request, md5, userId);
            uploadSessionMapper.insert(session);
        }

        List<Integer> uploadedChunks = queryUploadedChunkIndexes(session.getUploadId());
        InitUploadResponse response = new InitUploadResponse();
        response.setUploadId(session.getUploadId());
        response.setInstantUpload(false);
        response.setUploadedChunks(uploadedChunks);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadChunk(String uploadId,
                            String fileMd5,
                            Integer chunkIndex,
                            Integer totalChunks,
                            MultipartFile chunkFile,
                            String userId) throws IOException {
        // 分片上传（流式）：
        // - 参数校验（uploadId、chunkIndex、chunkFile）
        // - 校验分片对应的上传会话信息（md5 / totalChunks 等一致性检查）
        // - 如果分片已存在（DB 中记录），则直接返回（幂等）
        // - 从 MultipartFile 获取 InputStream，并直接调用 storageService.uploadRaw 将分片流上传到对象存储
        //   同时在上传过程中计算分片 MD5（storageService.uploadRaw 返回 MD5），避免在服务器上保存分片临时文件
        // - 将分片元信息写入 sys_file_chunk 表，并更新上传会话的 uploaded_chunks 计数与状态
        if (!StringUtils.hasText(uploadId)) {
            throw new IllegalArgumentException("uploadId 不能为空");
        }
        if (chunkIndex == null || chunkIndex < 0) {
            throw new IllegalArgumentException("chunkIndex 非法");
        }
        if (chunkFile == null || chunkFile.isEmpty()) {
            throw new IllegalArgumentException("chunk 文件不能为空");
        }

        FileUploadSessionEntity session = getSessionByUploadId(uploadId);
        if (session == null) {
            throw new IllegalArgumentException("上传会话不存在");
        }
        String md5 = normalizeMd5(fileMd5);
        if (!Objects.equals(session.getFileMd5(), md5)) {
            throw new IllegalArgumentException("文件MD5与会话不一致");
        }
        if (!Objects.equals(session.getTotalChunks(), totalChunks)) {
            throw new IllegalArgumentException("分片总数与会话不一致");
        }
        if (chunkIndex >= totalChunks) {
            throw new IllegalArgumentException("chunkIndex 超出范围");
        }

        QueryWrapper<FileChunkEntity> existsQw = new QueryWrapper<>();
        existsQw.eq("upload_id", uploadId).eq("chunk_index", chunkIndex);
        FileChunkEntity existedChunk = fileChunkMapper.selectOne(existsQw);
        if (existedChunk != null) {
            return;
        }

        // stream uploaded chunk directly to object storage and compute md5 during streaming
        long size = chunkFile.getSize();
        String chunkObject = chunkObjectName(uploadId, chunkIndex);
        String chunkMd5;
        try (java.io.InputStream in = chunkFile.getInputStream()) {
            chunkMd5 = storageService.uploadRaw(in, chunkObject, size);
        }

        FileChunkEntity chunkEntity = new FileChunkEntity();
        chunkEntity.setId(uuid());
        chunkEntity.setUploadId(uploadId);
        chunkEntity.setChunkIndex(chunkIndex);
        chunkEntity.setChunkSize(size);
        chunkEntity.setChunkMd5(chunkMd5);
        chunkEntity.setCreatedBy(userId);
        chunkEntity.setUpdatedBy(userId);
        fileChunkMapper.insert(chunkEntity);

        int uploadedCount = countUploadedChunks(uploadId);
        UpdateWrapper<FileUploadSessionEntity> update = new UpdateWrapper<>();
        update.eq("upload_id", uploadId)
                .set("uploaded_chunks", uploadedCount)
                .set("status", STATUS_UPLOADING)
                .set("last_chunk_at", new Timestamp(System.currentTimeMillis()))
                .set("updated_by", userId);
        uploadSessionMapper.update(null, update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileObjectVO completeUpload(CompleteUploadRequest request, String userId) throws IOException {
        // 完成上传 / 合并逻辑：
        // - 校验会话与 MD5
        // - 若会话已关联到已有对象（fileObjectId 不为空），直接返回该对象信息
        // - 查询所有分片元数据并确认数量与总分片数一致
        // - 按分片顺序从对象存储逐个下载分片流写入本地合并临时文件（merged.tmp）
        // - 对合并后的文件计算 MD5 与客户端传入值比对，若不一致则标记会话为失败并抛错
        // - 若已有相同 MD5 的对象（去重），则增加该对象的上传计数并将会话标记为已完成
        // - 否则：对合并文件进行加密（生成 DEK 并使用主密钥 wrap），将加密文件上传到对象存储（使用 multipart 优化）
        // - 在 sys_file_object 中写入对象记录（包含存储路径 storagePath、加密元数据等），并标记会话为完成
        // - 清理合并过程中的临时文件并删除对象存储中的分片对象与对应的分片 DB 记录
        String md5 = normalizeMd5(request.getFileMd5());
        validateMd5(md5);

        FileUploadSessionEntity session = getSessionByUploadId(request.getUploadId());
        if (session == null) {
            throw new IllegalArgumentException("上传会话不存在");
        }
        if (!Objects.equals(session.getFileMd5(), md5)) {
            throw new IllegalArgumentException("文件MD5与会话不一致");
        }

        if (StringUtils.hasText(session.getFileObjectId())) {
            FileObjectEntity linked = fileObjectMapper.selectById(session.getFileObjectId());
            if (linked != null) {
                return toVo(linked);
            }
        }

        List<FileChunkEntity> chunkEntities = queryChunkEntities(session.getUploadId());
        if (CollectionUtils.isEmpty(chunkEntities) || chunkEntities.size() != session.getTotalChunks()) {
            throw new IllegalStateException("分片未上传完整，无法合并");
        }

        Path mergedFile = mergedFilePath(session.getUploadId());
        mergeChunks(session.getUploadId(), chunkEntities, mergedFile);

        String mergedMd5 = FileHashUtils.md5(mergedFile);
        if (!md5.equalsIgnoreCase(mergedMd5)) {
            markSessionFailed(session.getUploadId(), userId);
            throw new IllegalStateException("文件完整性校验失败，MD5不一致");
        }

        FileObjectEntity existed = findFileObjectByMd5(md5);
        if (existed != null) {
            incUploadCount(existed, userId);
            markSessionDone(session.getUploadId(), existed.getId(), session.getTotalChunks(), userId);
            cleanupUploadWorkspace(session.getUploadId(), mergedFile);
            return toVo(existed);
        }

        String objectId = uuid();
        String ext = fileExt(session.getFileName());
        String objectName = objectRelativePath(objectId, ext);

        // upload encrypted content to storage (MinIO)
        FileCryptoService.EncryptionResult encryptionResult;
        String storagePath;
        try (java.io.InputStream in = Files.newInputStream(mergedFile)) {
            StorageService.UploadResult uploadResult = storageService.uploadEncrypted(in, objectName);
            encryptionResult = uploadResult.getEncryptionResult();
            storagePath = uploadResult.getStoragePath();
        }

        FileObjectEntity objectEntity = new FileObjectEntity();
        objectEntity.setId(objectId);
        objectEntity.setFileMd5(md5);
        objectEntity.setFileName(session.getFileName());
        objectEntity.setContentType(session.getContentType());
        objectEntity.setFileSize(session.getFileSize());
        objectEntity.setChunkSize(session.getChunkSize());
        objectEntity.setTotalChunks(session.getTotalChunks());
        objectEntity.setStoragePath(storagePath);
        objectEntity.setStorageSize(encryptionResult.getStorageSize());
        objectEntity.setEncryptAlgorithm(encryptionResult.getAlgorithm());
        objectEntity.setCipherIv(encryptionResult.getCipherIv());
        objectEntity.setWrapIv(encryptionResult.getWrapIv());
        objectEntity.setWrappedDek(encryptionResult.getWrappedDek());
        objectEntity.setStatus(1);
        objectEntity.setUploadCount(1);
        objectEntity.setCreatedBy(userId);
        objectEntity.setUpdatedBy(userId);
        fileObjectMapper.insert(objectEntity);

        markSessionDone(session.getUploadId(), objectId, session.getTotalChunks(), userId);
        cleanupUploadWorkspace(session.getUploadId(), mergedFile);
        return toVo(objectEntity);
    }

    @Override
    public UploadStatusResponse getStatus(String uploadId) {
        FileUploadSessionEntity session = getSessionByUploadId(uploadId);
        if (session == null) {
            throw new IllegalArgumentException("上传会话不存在");
        }

        UploadStatusResponse response = new UploadStatusResponse();
        response.setUploadId(session.getUploadId());
        response.setFileMd5(session.getFileMd5());
        response.setTotalChunks(session.getTotalChunks());
        response.setUploadedChunksCount(session.getUploadedChunks());
        response.setStatus(session.getStatus());
        response.setFileObjectId(session.getFileObjectId());
        response.setUploadedChunks(queryUploadedChunkIndexes(uploadId));
        return response;
    }

    @Override
    public Page<FileObjectVO> listObjects(String keyword, Integer page, Integer size) {
        // 若未传分页参数，默认返回前100条（兼容旧调用）
        if (page == null || size == null) {
            int realLimit =  limitOrDefault(50);
            QueryWrapper<FileObjectEntity> qw = new QueryWrapper<>();
            qw.eq("status", 1);
            if (StringUtils.hasText(keyword)) {
                qw.and(w -> w.like("file_name", keyword.trim()).or().like("file_md5", keyword.trim()));
            }
            qw.orderByDesc("created_at").last("FETCH FIRST " + realLimit + " ROWS ONLY");
            List<FileObjectEntity> entities = fileObjectMapper.selectList(qw);
            List<FileObjectVO> vos = entities.stream().map(this::toVo).collect(Collectors.toList());
            Page<FileObjectVO> p = new Page<>(1, vos.size());
            p.setRecords(vos);
            p.setTotal(vos.size());
            return p;
        }

        int realSize = Math.max(1, Math.min(size == null ? 10 : size, 200));
        int realPage = Math.max(1, page == null ? 1 : page);
        QueryWrapper<FileObjectEntity> qw = new QueryWrapper<>();
        qw.eq("status", 1);
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like("file_name", keyword.trim()).or().like("file_md5", keyword.trim()));
        }
        qw.orderByDesc("created_at");

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileObjectEntity> qpage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(realPage, realSize);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<FileObjectEntity> result =
                fileObjectMapper.selectPage(qpage, qw);

        Page<FileObjectVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<FileObjectVO> vos = result.getRecords().stream().map(this::toVo).collect(Collectors.toList());
        voPage.setRecords(vos);
        return voPage;
    }

    private int limitOrDefault(int def) {
        return def;
    }

    @Override
    public void downloadDecrypted(String fileId, HttpServletResponse response) throws IOException {
        FileObjectEntity objectEntity = fileObjectMapper.selectById(fileId);
        if (objectEntity == null || objectEntity.getStatus() == null || objectEntity.getStatus() != 1) {
            throw new IllegalArgumentException("文件不存在");
        }

        String fileName = objectEntity.getFileName();
        String encodedName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        response.setCharacterEncoding("UTF-8");
        response.setContentType(StringUtils.hasText(objectEntity.getContentType()) ? objectEntity.getContentType() : "application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);

        // use storage service to download and decrypt directly to response output
        storageService.downloadDecrypted(
                objectEntity.getStoragePath(),
                objectEntity.getCipherIv(),
                objectEntity.getWrapIv(),
                objectEntity.getWrappedDek(),
                response.getOutputStream()
        );
        response.flushBuffer();
    }

    @Override
    public void deleteFile(String fileId, String userId) throws IOException {
        FileObjectEntity object = fileObjectMapper.selectById(fileId);
        if (object == null) {
            throw new IllegalArgumentException("文件不存在");
        }

        // 删除存储文件（MinIO）
        try {
            storageService.delete(object.getStoragePath());
        } catch (IOException e) {
            throw new IOException("删除存储文件失败", e);
        }

        // 删除数据库记录
        fileObjectMapper.deleteById(fileId);

        // 清除可能指向该对象的上传会话
        UpdateWrapper<FileUploadSessionEntity> uw = new UpdateWrapper<>();
        uw.eq("file_object_id", fileId).set("file_object_id", null).set("updated_by", userId);
        uploadSessionMapper.update(null, uw);
    }

    private FileUploadSessionEntity createSession(InitUploadRequest request, String md5, String userId) {
        FileUploadSessionEntity session = new FileUploadSessionEntity();
        session.setId(uuid());
        session.setUploadId(uuid());
        session.setFileMd5(md5);
        session.setFileName(request.getFileName());
        session.setContentType(request.getContentType());
        session.setFileSize(request.getFileSize());
        session.setChunkSize(request.getChunkSize());
        session.setTotalChunks(request.getTotalChunks());
        session.setUploadedChunks(0);
        session.setStatus(STATUS_INIT);
        session.setCreatedBy(userId);
        session.setUpdatedBy(userId);
        return session;
    }

    private FileUploadSessionEntity findLatestActiveSession(String md5, Long fileSize, Integer totalChunks, Long chunkSize) {
        QueryWrapper<FileUploadSessionEntity> qw = new QueryWrapper<>();
        qw.eq("file_md5", md5)
                .eq("file_size", fileSize)
                .eq("total_chunks", totalChunks)
                .eq("chunk_size", chunkSize)
                .in("status", STATUS_INIT, STATUS_UPLOADING)
                .orderByDesc("updated_at");
        List<FileUploadSessionEntity> sessions = uploadSessionMapper.selectList(qw);
        return sessions.isEmpty() ? null : sessions.get(0);
    }

    private FileUploadSessionEntity getSessionByUploadId(String uploadId) {
        QueryWrapper<FileUploadSessionEntity> qw = new QueryWrapper<>();
        qw.eq("upload_id", uploadId);
        return uploadSessionMapper.selectOne(qw);
    }

    private FileObjectEntity findFileObjectByMd5(String md5) {
        QueryWrapper<FileObjectEntity> qw = new QueryWrapper<>();
        qw.eq("file_md5", md5).eq("status", 1);
        return fileObjectMapper.selectOne(qw);
    }

    private List<FileChunkEntity> queryChunkEntities(String uploadId) {
        QueryWrapper<FileChunkEntity> qw = new QueryWrapper<>();
        qw.eq("upload_id", uploadId).orderByAsc("chunk_index");
        return fileChunkMapper.selectList(qw);
    }

    private List<Integer> queryUploadedChunkIndexes(String uploadId) {
        return queryChunkEntities(uploadId).stream()
                .sorted(Comparator.comparing(FileChunkEntity::getChunkIndex))
                .map(FileChunkEntity::getChunkIndex)
                .collect(Collectors.toList());
    }

    private int countUploadedChunks(String uploadId) {
        QueryWrapper<FileChunkEntity> countQw = new QueryWrapper<>();
        countQw.eq("upload_id", uploadId);
        Long count = fileChunkMapper.selectCount(countQw);
        return count == null ? 0 : count.intValue();
    }

    private void markSessionDone(String uploadId, String fileObjectId, Integer totalChunks, String userId) {
        UpdateWrapper<FileUploadSessionEntity> update = new UpdateWrapper<>();
        update.eq("upload_id", uploadId)
                .set("status", STATUS_COMPLETED)
                .set("file_object_id", fileObjectId)
                .set("uploaded_chunks", totalChunks)
                .set("updated_by", userId)
                .set("last_chunk_at", new Timestamp(System.currentTimeMillis()));
        uploadSessionMapper.update(null, update);
    }

    private void markSessionFailed(String uploadId, String userId) {
        UpdateWrapper<FileUploadSessionEntity> update = new UpdateWrapper<>();
        update.eq("upload_id", uploadId)
                .set("status", STATUS_FAILED)
                .set("updated_by", userId)
                .set("last_chunk_at", new Timestamp(System.currentTimeMillis()));
        uploadSessionMapper.update(null, update);
    }

    private void incUploadCount(FileObjectEntity entity, String userId) {
        int current = entity.getUploadCount() == null ? 0 : entity.getUploadCount();
        entity.setUploadCount(current + 1);
        entity.setUpdatedBy(userId);
        fileObjectMapper.updateById(entity);
    }

    private void mergeChunks(String uploadId, List<FileChunkEntity> chunks, Path mergedFile) throws IOException {
        // 合并分片：从对象存储下载每个分片流并顺序写入到 mergedFile
        // 注意：此方法在合并前已确保 chunks 列表按 chunk_index 升序排列
        Files.createDirectories(mergedFile.getParent());
        if (Files.exists(mergedFile)) {
            Files.delete(mergedFile);
        }

        try (java.io.OutputStream out = Files.newOutputStream(mergedFile, StandardOpenOption.CREATE_NEW)) {
            for (FileChunkEntity chunk : chunks) {
                String chunkObject = chunkObjectName(uploadId, chunk.getChunkIndex());
                // 从对象存储获取分片流并写入合并输出流
                try (java.io.InputStream in = storageService.downloadRaw(chunkObject)) {
                    if (in == null) {
                        throw new IllegalStateException("缺少分片文件: " + chunk.getChunkIndex());
                    }
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        }
    }

    private void cleanupUploadWorkspace(String uploadId, Path mergedFile) {
        try {
            // 删除本地合并临时文件（如果存在）
            if (Files.exists(mergedFile)) {
                Files.delete(mergedFile);
            }

            // 删除 MinIO 上的分片对象，并清理分片元数据表
            List<FileChunkEntity> chunks = queryChunkEntities(uploadId);
            for (FileChunkEntity chunk : chunks) {
                String chunkObject = chunkObjectName(uploadId, chunk.getChunkIndex());
                try {
                    storageService.delete(chunkObject);
                } catch (IOException ignored) {
                }
            }
            // 从 DB 中删除该上传会话相关的分片记录，避免数据积累
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FileChunkEntity> qw = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            qw.eq("upload_id", uploadId);
            fileChunkMapper.delete(qw);
        } catch (IOException e) {
            log.warn("清理上传临时目录失败, uploadId={}", uploadId, e);
        }
    }

    private String normalizeMd5(String md5) {
        if (!StringUtils.hasText(md5)) {
            return "";
        }
        return md5.trim().toLowerCase(Locale.ROOT);
    }

    private void validateMd5(String md5) {
        if (!md5.matches("^[a-f0-9]{32}$")) {
            throw new IllegalArgumentException("文件MD5格式非法");
        }
    }

    private Path mergedFilePath(String uploadId) {
        // 使用系统临时目录作为合并时的临时文件位置，避免依赖已移除的本地存储配置
        String tmp = System.getProperty("java.io.tmpdir");
        Path dir = Paths.get(tmp).resolve("dataSpace").resolve(uploadId);
        return dir.resolve("merged.tmp");
    }

    private String objectRelativePath(String fileId, String ext) {
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String normalizedExt = StringUtils.hasText(ext) ? ext : "bin";
        return month + "/" + fileId + "." + normalizedExt;
    }

    private String chunkObjectName(String uploadId, Integer chunkIndex) {
        return "chunks/" + uploadId + "/" + chunkIndex + ".part";
    }

    private String fileExt(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "bin";
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).trim();
        return ext.isEmpty() ? "bin" : ext;
    }

    private FileObjectVO toVo(FileObjectEntity entity) {
        FileObjectVO vo = new FileObjectVO();
        vo.setId(entity.getId());
        vo.setFileName(entity.getFileName());
        vo.setContentType(entity.getContentType());
        vo.setFileSize(entity.getFileSize());
        vo.setFileMd5(entity.getFileMd5());
        vo.setTotalChunks(entity.getTotalChunks());
        vo.setUploadCount(entity.getUploadCount());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

