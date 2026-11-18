package com.qidirect.mall.infra.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地磁盘存储，供本地开发使用。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Path root;
    private final String publicUrl;

    public LocalFileStorageService(@Value("${storage.local-dir:./uploads}") String localDir,
                                   @Value("${storage.public-url:/files}") String publicUrl) {
        this.root = Paths.get(localDir).toAbsolutePath().normalize();
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    @Override
    public String upload(byte[] content, String originalFilename, String contentType) {
        String dateDir = LocalDate.now().format(DATE_DIR);
        String filename = UUID.randomUUID().toString().replace("-", "") + extension(originalFilename);
        try {
            Path dir = root.resolve(dateDir);
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), content);
        } catch (IOException e) {
            throw new UncheckedIOException("文件写入失败", e);
        }
        return publicUrl + "/" + dateDir + "/" + filename;
    }

    private String extension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int index = originalFilename.lastIndexOf('.');
        return index < 0 ? "" : originalFilename.substring(index);
    }
}
