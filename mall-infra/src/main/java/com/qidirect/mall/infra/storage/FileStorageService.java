package com.qidirect.mall.infra.storage;

/**
 * 文件存储。生产默认走 OSS，本地开发可用 local。
 */
public interface FileStorageService {

    /**
     * 上传文件并返回可访问地址。
     */
    String upload(byte[] content, String originalFilename, String contentType);
}
