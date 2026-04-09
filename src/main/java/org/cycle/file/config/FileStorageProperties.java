package org.cycle.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "file.storage")
public class FileStorageProperties {

    /**
     * 文件工作空间根目录。
     */
    private String workspace = "data/file-platform";

    /**
     * 分片临时目录。
     */
    private String chunkDir = "chunks";

    /**
     * 加密文件目录。
     */
    private String objectDir = "objects";

    /**
     * AES 主密钥（Base64，解码后必须为32字节）。
     */
    private String masterKeyBase64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
}
