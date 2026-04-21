package org.cycle.file.service;

import lombok.Data;
import org.cycle.file.config.FileStorageProperties;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class FileCryptoService {

    private static final String AES_ALGORITHM = "AES";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;

    private final FileStorageProperties properties;
    private SecretKey masterKey;

    public FileCryptoService(FileStorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        byte[] key = Base64.getDecoder().decode(properties.getMasterKeyBase64());
        if (key.length != 32) {
            throw new IllegalStateException("file.storage.master-key-base64 必须是 32 字节 Base64 密钥");
        }
        this.masterKey = new SecretKeySpec(key, AES_ALGORITHM);
    }

    public EncryptionResult encrypt(Path plainFile, Path encryptedFile) throws IOException {
        try {
            SecretKey dek = generateDataKey();
            byte[] fileIv = randomBytes(IV_LENGTH);
            byte[] wrapIv = randomBytes(IV_LENGTH);
            byte[] wrappedDek = encryptBytes(dek.getEncoded(), masterKey, wrapIv);

            Files.createDirectories(encryptedFile.getParent());
            try (InputStream in = Files.newInputStream(plainFile);
                 OutputStream out = Files.newOutputStream(encryptedFile);
                 CipherOutputStream cipherOut = new CipherOutputStream(out, initCipher(Cipher.ENCRYPT_MODE, dek, fileIv))) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    cipherOut.write(buffer, 0, len);
                }
            }

            EncryptionResult result = new EncryptionResult();
            result.setAlgorithm(AES_GCM);
            result.setCipherIv(Base64.getEncoder().encodeToString(fileIv));
            result.setWrapIv(Base64.getEncoder().encodeToString(wrapIv));
            result.setWrappedDek(Base64.getEncoder().encodeToString(wrappedDek));
            result.setStorageSize(Files.size(encryptedFile));
            return result;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("文件加密失败", e);
        }
    }

    /**
     * 基于流的加密：从传入的 InputStream 读取原始数据，将加密后的字节写入到 OutputStream。
     * 返回包含加密元数据的 EncryptionResult（包括写入的字节数 storageSize）。
     */
    public EncryptionResult encrypt(InputStream in, OutputStream out) throws IOException {
        try {
            SecretKey dek = generateDataKey();
            byte[] fileIv = randomBytes(IV_LENGTH);
            byte[] wrapIv = randomBytes(IV_LENGTH);
            byte[] wrappedDek = encryptBytes(dek.getEncoded(), masterKey, wrapIv);

            CountingOutputStream countingOut = new CountingOutputStream(out);
            try (CipherOutputStream cipherOut = new CipherOutputStream(countingOut, initCipher(Cipher.ENCRYPT_MODE, dek, fileIv))) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    cipherOut.write(buffer, 0, len);
                }
            }

            EncryptionResult result = new EncryptionResult();
            result.setAlgorithm(AES_GCM);
            result.setCipherIv(Base64.getEncoder().encodeToString(fileIv));
            result.setWrapIv(Base64.getEncoder().encodeToString(wrapIv));
            result.setWrappedDek(Base64.getEncoder().encodeToString(wrappedDek));
            result.setStorageSize(countingOut.getCount());
            return result;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("文件加密失败", e);
        }
    }

    /**
     * 基于流的解密：从传入的加密数据 InputStream 读取，解密后写入到指定的 OutputStream。
     */
    public void decrypt(InputStream encryptedIn,
                        String cipherIvBase64,
                        String wrapIvBase64,
                        String wrappedDekBase64,
                        OutputStream out) throws IOException {
        try {
            byte[] cipherIv = Base64.getDecoder().decode(cipherIvBase64);
            byte[] wrapIv = Base64.getDecoder().decode(wrapIvBase64);
            byte[] wrappedDek = Base64.getDecoder().decode(wrappedDekBase64);

            byte[] dekBytes = decryptBytes(wrappedDek, masterKey, wrapIv);
            SecretKey dek = new SecretKeySpec(dekBytes, AES_ALGORITHM);

            try (CipherInputStream cipherIn = new CipherInputStream(encryptedIn, initCipher(Cipher.DECRYPT_MODE, dek, cipherIv))) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = cipherIn.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("文件解密失败", e);
        }
    }

    public void decryptToStream(Path encryptedFile,
                                String cipherIvBase64,
                                String wrapIvBase64,
                                String wrappedDekBase64,
                                OutputStream out) throws IOException {
        try {
            byte[] cipherIv = Base64.getDecoder().decode(cipherIvBase64);
            byte[] wrapIv = Base64.getDecoder().decode(wrapIvBase64);
            byte[] wrappedDek = Base64.getDecoder().decode(wrappedDekBase64);

            byte[] dekBytes = decryptBytes(wrappedDek, masterKey, wrapIv);
            SecretKey dek = new SecretKeySpec(dekBytes, AES_ALGORITHM);

            try (InputStream in = Files.newInputStream(encryptedFile);
                 CipherInputStream cipherIn = new CipherInputStream(in, initCipher(Cipher.DECRYPT_MODE, dek, cipherIv))) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = cipherIn.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("文件解密失败", e);
        }
    }

    private SecretKey generateDataKey() throws GeneralSecurityException {
        KeyGenerator keyGenerator = KeyGenerator.getInstance(AES_ALGORITHM);
        keyGenerator.init(256);
        return keyGenerator.generateKey();
    }

    private Cipher initCipher(int mode, SecretKey key, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(mode, key, new GCMParameterSpec(TAG_LENGTH, iv));
        return cipher;
    }

    private byte[] encryptBytes(byte[] plain, SecretKey key, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = initCipher(Cipher.ENCRYPT_MODE, key, iv);
        return cipher.doFinal(plain);
    }

    private byte[] decryptBytes(byte[] encrypted, SecretKey key, byte[] iv) throws GeneralSecurityException {
        Cipher cipher = initCipher(Cipher.DECRYPT_MODE, key, iv);
        return cipher.doFinal(encrypted);
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    /**
     * Simple CountingOutputStream to measure bytes written.
     */
    private static class CountingOutputStream extends java.io.FilterOutputStream {
        private long count = 0;

        CountingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }

        long getCount() {
            return count;
        }
    }

    @Data
    public static class EncryptionResult {
        private String algorithm;
        private String cipherIv;
        private String wrapIv;
        private String wrappedDek;
        private Long storageSize;
    }
}

