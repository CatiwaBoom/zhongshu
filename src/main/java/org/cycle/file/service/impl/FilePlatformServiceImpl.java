package org.cycle.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cycle.file.config.FileStorageProperties;
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
import java.util.stream.Stream;

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
    private final FileCryptoService fileCryptoService;
    private final FileStorageProperties storageProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InitUploadResponse initUpload(InitUploadRequest request, String userId) {
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

        Path chunkPath = chunkFilePath(uploadId, chunkIndex);
        Files.createDirectories(chunkPath.getParent());
        chunkFile.transferTo(chunkPath.toFile());

        FileChunkEntity chunkEntity = new FileChunkEntity();
        chunkEntity.setId(uuid());
        chunkEntity.setUploadId(uploadId);
        chunkEntity.setChunkIndex(chunkIndex);
        chunkEntity.setChunkSize(Files.size(chunkPath));
        chunkEntity.setChunkMd5(FileHashUtils.md5(chunkPath));
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
        Path encryptedPath = objectPath(objectId, ext);

        FileCryptoService.EncryptionResult encryptionResult = fileCryptoService.encrypt(mergedFile, encryptedPath);

        FileObjectEntity objectEntity = new FileObjectEntity();
        objectEntity.setId(objectId);
        objectEntity.setFileMd5(md5);
        objectEntity.setFileName(session.getFileName());
        objectEntity.setContentType(session.getContentType());
        objectEntity.setFileSize(session.getFileSize());
        objectEntity.setChunkSize(session.getChunkSize());
        objectEntity.setTotalChunks(session.getTotalChunks());
        objectEntity.setStoragePath(encryptedPath.toString());
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
    public List<FileObjectVO> listObjects(String keyword, Integer limit) {
        int realLimit = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        QueryWrapper<FileObjectEntity> qw = new QueryWrapper<>();
        qw.eq("status", 1);
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like("file_name", keyword.trim()).or().like("file_md5", keyword.trim()));
        }
        qw.orderByDesc("created_at").last("FETCH FIRST " + realLimit + " ROWS ONLY");
        List<FileObjectEntity> entities = fileObjectMapper.selectList(qw);
        return entities.stream().map(this::toVo).collect(Collectors.toList());
    }

    @Override
    public void downloadDecrypted(String fileId, HttpServletResponse response) throws IOException {
        FileObjectEntity objectEntity = fileObjectMapper.selectById(fileId);
        if (objectEntity == null || objectEntity.getStatus() == null || objectEntity.getStatus() != 1) {
            throw new IllegalArgumentException("文件不存在");
        }

        Path encryptedPath = Paths.get(objectEntity.getStoragePath());
        if (!Files.exists(encryptedPath)) {
            throw new IllegalStateException("文件存储路径不存在");
        }

        String fileName = objectEntity.getFileName();
        String encodedName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        response.setCharacterEncoding("UTF-8");
        response.setContentType(StringUtils.hasText(objectEntity.getContentType()) ? objectEntity.getContentType() : "application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);

        fileCryptoService.decryptToStream(
                encryptedPath,
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

        // 删除存储文件
        Path encryptedPath = Paths.get(object.getStoragePath());
        try {
            if (Files.exists(encryptedPath)) Files.delete(encryptedPath);
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
        Files.createDirectories(mergedFile.getParent());
        if (Files.exists(mergedFile)) {
            Files.delete(mergedFile);
        }

        try (java.io.OutputStream out = Files.newOutputStream(mergedFile, StandardOpenOption.CREATE_NEW)) {
            for (FileChunkEntity chunk : chunks) {
                Path chunkPath = chunkFilePath(uploadId, chunk.getChunkIndex());
                if (!Files.exists(chunkPath)) {
                    throw new IllegalStateException("缺少分片文件: " + chunk.getChunkIndex());
                }
                Files.copy(chunkPath, out);
            }
        }
    }

    private void cleanupUploadWorkspace(String uploadId, Path mergedFile) {
        try {
            if (Files.exists(mergedFile)) {
                Files.delete(mergedFile);
            }
            Path chunkDir = chunkRootPath().resolve(uploadId);
            if (Files.exists(chunkDir)) {
                try (Stream<Path> stream = Files.walk(chunkDir)) {
                    stream.sorted(Comparator.reverseOrder())
                            .forEach(path -> {
                                try {
                                    Files.delete(path);
                                } catch (IOException ignored) {
                                }
                            });
                }
            }
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

    private Path chunkRootPath() {
        return Paths.get(storageProperties.getWorkspace()).resolve(storageProperties.getChunkDir());
    }

    private Path objectRootPath() {
        return Paths.get(storageProperties.getWorkspace()).resolve(storageProperties.getObjectDir());
    }

    private Path chunkFilePath(String uploadId, Integer chunkIndex) {
        return chunkRootPath().resolve(uploadId).resolve(chunkIndex + ".part");
    }

    private Path mergedFilePath(String uploadId) {
        return chunkRootPath().resolve(uploadId).resolve("merged.tmp");
    }

    private Path objectPath(String fileId, String ext) {
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String normalizedExt = StringUtils.hasText(ext) ? ext : "bin";
        return objectRootPath().resolve(month).resolve(fileId + "." + normalizedExt);
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

