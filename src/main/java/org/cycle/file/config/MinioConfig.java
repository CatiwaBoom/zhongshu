package org.cycle.file.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@Configuration
@ConditionalOnProperty(prefix = "file.storage.minio", name = "endpoint")
@Slf4j
public class MinioConfig {

    private final FileStorageProperties properties;

    public MinioConfig(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Bean
    public MinioClient minioClient() {
        FileStorageProperties.Minio m = properties.getMinio();
        MinioClient.Builder builder = MinioClient.builder().credentials(m.getAccessKey(), m.getSecretKey());
        String endpoint = m.getEndpoint();
        try {
            if (endpoint == null || endpoint.trim().isEmpty()) {
                throw new IllegalStateException("file.storage.minio.endpoint 未配置");
            }
            // 如果 endpoint 包含 scheme（http/https），直接使用完整 endpoint；否则使用 host/port/secure 形式
            java.net.URI uri = new java.net.URI(endpoint);
            String scheme = uri.getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                // 保持原始 endpoint 字符串（包含 scheme）以让 MinIO SDK 正确处理
                builder.endpoint(endpoint);
            } else {
                // 没有 scheme，尝试按 host:port 格式解析
                String host = uri.getHost();
                int port = uri.getPort();
                if (host == null) {
                    // 尝试简单拆分 host:port
                    String[] parts = endpoint.split(":");
                    host = parts[0];
                    port = parts.length > 1 ? Integer.parseInt(parts[1]) : (m.isSecure() ? 443 : 9000);
                }
                builder.endpoint(host, port, m.isSecure());
            }
        } catch (Exception e) {
            // 如果解析失败，回退到直接使用配置的 endpoint 字符串
            builder.endpoint(endpoint);
        }
        return builder.build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureBucket(ApplicationReadyEvent evt) {
        FileStorageProperties.Minio m = properties.getMinio();
        if (!m.isAutoCreateBucket()) {
            return;
        }
        try {
            // 在应用启动完成后使用已创建的 MinioClient bean 检查并创建桶，避免循环依赖
            MinioClient client = minioClient();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(m.getBucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(m.getBucket()).build());
                log.info("已创建 MinIO 存储桶：{}", m.getBucket());
            }
        } catch (Exception e) {
            log.error("确保 MinIO 存储桶 {} 失败", m.getBucket(), e);
        }
    }
}

