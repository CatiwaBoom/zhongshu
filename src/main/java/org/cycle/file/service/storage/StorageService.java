package org.cycle.file.service.storage;

import org.cycle.file.service.FileCryptoService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface StorageService {

    // 上传结果：包含存储路径（例如 minio://bucket/object）和加密元信息
    class UploadResult {
        private String storagePath;
        private FileCryptoService.EncryptionResult encryptionResult;

        public UploadResult(String storagePath, FileCryptoService.EncryptionResult encryptionResult) {
            this.storagePath = storagePath;
            this.encryptionResult = encryptionResult;
        }

        public String getStoragePath() {
            return storagePath;
        }

        public FileCryptoService.EncryptionResult getEncryptionResult() {
            return encryptionResult;
        }
    }

    /**
     * 上传并加密：将明文流上传到存储，存储实现应在上传前对数据进行加密并返回加密元数据。
     * @param plainInput 明文输入流（调用方负责关闭）
     * @param objectName 存储中目标对象名（例如对象路径/Key）
     */
    UploadResult uploadEncrypted(InputStream plainInput, String objectName) throws IOException;

    /**
     * 原始字节上传（不加密），通常用于分片上传。
     * 实现应在流式上传过程中计算并返回该分片的 MD5（16 进制小写字符串）。
     * @param in 输入流
     * @param objectName 对象名/Key
     * @param size 字节长度
     */
    String uploadRaw(InputStream in, String objectName, long size) throws IOException;

    /**
     * 下载原始对象流，调用方负责关闭返回的 InputStream。
     */
    InputStream downloadRaw(String objectName) throws IOException;

    /**
     * 下载加密对象并解密到提供的 OutputStream，使用传入的加密元数据（cipherIv/wrapIv/wrappedDek）。
     */
    void downloadDecrypted(String objectName,
                           String cipherIvBase64,
                           String wrapIvBase64,
                           String wrappedDekBase64,
                           OutputStream out) throws IOException;

    /**
     * 从存储中删除对象。
     */
    void delete(String objectName) throws IOException;
}

