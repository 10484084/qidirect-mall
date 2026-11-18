package com.qidirect.mall.infra.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地存储模式下把上传目录挂到 /files/** 供图片访问；OSS 模式由 OSS 域名直出，无需映射。
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageWebConfig implements WebMvcConfigurer {

    private final String publicUrl;
    private final String location;

    public LocalStorageWebConfig(@Value("${storage.public-url:/files}") String publicUrl,
                                 @Value("${storage.local-dir:./uploads}") String localDir) {
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
        Path root = Paths.get(localDir).toAbsolutePath().normalize();
        this.location = root.toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(publicUrl + "/**")
                .addResourceLocations(location);
    }
}
