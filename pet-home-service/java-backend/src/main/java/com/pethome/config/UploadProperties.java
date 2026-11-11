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

    /** 存储类型：local 本地磁盘 / oss 对象存储 */
    private String storageType = "local";

    /** 本地存储根目录（相对路径基于服务启动目录） */
    private String baseDir = "./upload";

    /** 对外访问前缀，需与静态资源映射保持一致 */
    private String urlPrefix = "/upload/";

    /** 单文件大小上限（字节），默认 5MB */
    private long maxSize = 5 * 1024 * 1024L;

    /** 允许上传的图片扩展名 */
    private List<String> allowedExtensions = new ArrayList<>(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

    /** 对象存储配置（storage-type=oss 时使用） */
    private Oss oss = new Oss();

    /**
     * 对象存储配置
     */
    @Data
    public static class Oss {

        /** 服务地址，例如 https://oss-cn-hangzhou.aliyuncs.com */
        private String endpoint;

        /** 存储桶名称 */
        private String bucket;

        /** 访问密钥 */
        private String accessKey;

        /** 访问密钥 Secret */
        private String secretKey;

        /** 对外访问域名（CDN 或桶域名） */
        private String publicUrlPrefix;
    }

    /**
     * 归一化后的访问前缀，保证以 / 结尾
     */
    public String normalizedUrlPrefix() {
        return urlPrefix.endsWith("/") ? urlPrefix : urlPrefix + "/";
    }

}
