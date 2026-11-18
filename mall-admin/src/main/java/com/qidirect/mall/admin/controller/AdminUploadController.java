package com.qidirect.mall.admin.controller;

import com.qidirect.mall.common.api.ApiResponse;
import com.qidirect.mall.common.exception.BizException;
import com.qidirect.mall.common.exception.ErrorCode;
import com.qidirect.mall.infra.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Set;

/**
 * 后台文件上传：凭证、证件照、纸质协议扫描件、商品图等。
 */
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class AdminUploadController {

    static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "pdf");
    static final long MAX_SIZE = 5L * 1024 * 1024;

    private final FileStorageService fileStorageService;

    @PostMapping
    public ApiResponse<String> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(store(file, fileStorageService));
    }

    /**
     * 校验扩展名与大小后写入存储，返回可访问地址。
     */
    static String store(MultipartFile file, FileStorageService storage) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "请选择文件");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "文件不能超过 5MB");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int index = name.lastIndexOf('.');
        String ext = index < 0 ? "" : name.substring(index + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException(ErrorCode.PARAM_ERROR.getCode(), "仅支持 jpg/png/webp/pdf 文件");
        }
        try {
            return storage.upload(file.getBytes(), name, file.getContentType());
        } catch (IOException e) {
            throw new UncheckedIOException("文件读取失败", e);
        }
    }
}
