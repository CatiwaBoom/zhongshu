package org.cycle.file.service.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.UploadObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.cycle.file.config.FileStorageProperties;
import org.cycle.file.service.FileCryptoService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
@ConditionalOnProperty(prefix = "file.storage.minio", name = "endpoint")
@Slf4j
public class MinioStorageService implements StorageService {

    private final MinioClient minioClient;
    private final FileStorageProperties.Minio minioProps;
    private final FileCryptoService fileCryptoService;

    public MinioStorageService(MinioClient minioClient, FileStorageProperties properties, FileCryptoService fileCryptoService) {
        this.minioClient = minioClient;
        this.minioProps = properties.getMinio();
        this.fileCryptoService = fileCryptoService;
    }

    @Override
    public UploadResult uploadEncrypted(InputStream plainInput, String objectName) throws IOException {
        // 将明文流加密写入临时文件，然后上传到 MinIO
        Path temp = Files.createTempFile("enc-", ".bin");
        try (OutputStream out = Files.newOutputStream(temp)) {
            FileCryptoService.EncryptionResult enc = fileCryptoService.encrypt(plainInput, out);
            // 使用 uploadObject（基于本地文件路径），以便 MinIO 客户端在需要时使用 multipart 上传优化大文件
            UploadObjectArgs uargs = UploadObjectArgs.builder()
                    .bucket(minioProps.getBucket())
                    .object(objectName)
                    .filename(temp.toString())
                    .contentType("application/octet-stream")
                    .build();
            minioClient.uploadObject(uargs);
            String storagePath = "minio://" + minioProps.getBucket() + "/" + objectName;
            return new UploadResult(storagePath, enc);
        } catch (Exception e) {
            throw new IOException("上传加密对象到 MinIO 失败", e);
        } finally {
            try {
                Files.deleteIfExists(temp);
            } catch (IOException e) {
                log.warn("删除临时文件 {} 失败", temp, e);
            }
        }
    }

    @Override
    public String uploadRaw(InputStream in, String objectName, long size) throws IOException {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            java.security.DigestInputStream dis = new java.security.DigestInputStream(in, md);

            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(minioProps.getBucket())
                    .object(objectName)
                    .stream(dis, size, -1)
                    .contentType("application/octet-stream")
                    .build();
            minioClient.putObject(args);

            byte[] digest = md.digest();
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new IOException("上传原始对象到 MinIO 失败", e);
        }
    }

    @Override
    public InputStream downloadRaw(String objectName) throws IOException {
        try {
            String[] bo = resolveBucketObject(objectName);
            return minioClient.getObject(GetObjectArgs.builder().bucket(bo[0]).object(bo[1]).build());
        } catch (Exception e) {
            throw new IOException("从 MinIO 获取对象失败", e);
        }
    }

    @Override
    public void downloadDecrypted(String objectName, String cipherIvBase64, String wrapIvBase64, String wrappedDekBase64, OutputStream out) throws IOException {
        try (InputStream in = minioClient.getObject(GetObjectArgs.builder().bucket(resolveBucketObject(objectName)[0]).object(resolveBucketObject(objectName)[1]).build())) {
            fileCryptoService.decrypt(in, cipherIvBase64, wrapIvBase64, wrappedDekBase64, out);
        } catch (Exception e) {
            throw new IOException("从 MinIO 下载或解密对象失败", e);
        }
    }

    @Override
    public void delete(String objectName) throws IOException {
        try {
            String[] bo = resolveBucketObject(objectName);
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bo[0]).object(bo[1]).build());
        } catch (Exception e) {
            throw new IOException("从 MinIO 删除对象失败", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private String[] resolveBucketObject(String pathOrObject) {
        if (pathOrObject == null) {
            return new String[] {minioProps.getBucket(), ""};
        }
        if (pathOrObject.startsWith("minio://")) {
            String remainder = pathOrObject.substring("minio://".length());
            int idx = remainder.indexOf('/');
            if (idx <= 0) {
                return new String[] {minioProps.getBucket(), remainder};
            }
            String bucket = remainder.substring(0, idx);
            String object = remainder.substring(idx + 1);
            return new String[] {bucket, object};
        }
        return new String[] {minioProps.getBucket(), pathOrObject};
    }
}

