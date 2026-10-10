package com.pethome.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件上传配置，对应 application.yaml 中的 pethome.upload
 *
 * @author pet-home
 */
@Data
@Component
@ConfigurationProperties(prefix = "pethome.upload")
public class UploadProperties {

    /** 本地存储根目录（相对路径基于服务启动目录） */
    private String baseDir = "./upload";

    /** 对外访问前缀，需与静态资源映射保持一致 */
    private String urlPrefix = "/upload/";

    /** 单文件大小上限（字节），默认 5MB */
    private long maxSize = 5 * 1024 * 1024L;

    /** 允许上传的图片扩展名 */
    private List<String> allowedExtensions = new ArrayList<>(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

    /**
     * 归一化后的访问前缀，保证以 / 结尾
     */
    public String normalizedUrlPrefix() {
        return urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
    }

}
