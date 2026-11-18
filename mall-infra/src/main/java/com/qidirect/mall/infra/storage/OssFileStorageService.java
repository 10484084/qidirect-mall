package com.qidirect.mall.infra.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 阿里云 OSS 存储，storage.type=oss 时启用。文件按日期分目录。
 */
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "oss")
public class OssFileStorageService implements FileStorageService {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OSS ossClient;
    private final String bucket;
    private final String publicUrl;

    public OssFileStorageService(@Value("${storage.oss.endpoint}") String endpoint,
                                 @Value("${storage.oss.bucket}") String bucket,
                                 @Value("${storage.oss.access-key}") String accessKey,
                                 @Value("${storage.oss.secret}") String secret,
                                 @Value("${storage.oss.public-url}") String publicUrl) {
        this.ossClient = new OSSClientBuilder().build(endpoint, accessKey, secret);
        this.bucket = bucket;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    @Override
    public String upload(byte[] content, String originalFilename, String contentType) {
        String key = LocalDate.now().format(DATE_DIR) + "/"
                + UUID.randomUUID().toString().replace("-", "") + extension(originalFilename);
        ossClient.putObject(bucket, key, new ByteArrayInputStream(content));
        return publicUrl + "/" + key;
    }

    private String extension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int index = originalFilename.lastIndexOf('.');
        return index < 0 ? "" : originalFilename.substring(index);
    }

    @PreDestroy
    public void shutdown() {
        ossClient.shutdown();
    }
}
