package org.cycle.file.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "file.storage")
public class FileStorageProperties {

    /**
     * AES 主密钥（Base64，解码后必须为32字节）。
     */
    private String masterKeyBase64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    /**
     * MinIO 配置
     */
    private Minio minio = new Minio();

    @Data
    public static class Minio {
        /** MinIO 服务地址，例如 http://127.0.0.1:9000 */
        private String endpoint;

        /** Access key */
        private String accessKey = "admin";

        /** Secret key */
        private String secretKey = "minIO@root.123";

        /** 存储桶名称 */
        private String bucket = "data-space";

        /** 是否使用 https */
        private boolean secure = false;

        /** 启动时是否自动创建桶 */
        private boolean autoCreateBucket = true;
    }
}
