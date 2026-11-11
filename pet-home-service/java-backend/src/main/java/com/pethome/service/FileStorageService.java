package com.pethome.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务抽象。
 *
 * <p>实现由 {@code pethome.upload.storage-type} 决定：{@code local} 落本地磁盘，
 * {@code oss} 走对象存储。业务层只依赖本接口，切换存储不需要改调用方。</p>
 *
 * @author pet-home
 */
public interface FileStorageService {

    /**
     * 保存图片并返回可访问地址
     *
     * @param file 上传的图片
     * @return 形如 /upload/2026/10/10/xxx.png 或 https://cdn/xxx.png 的访问地址
     */
    String storeImage(MultipartFile file);

}
