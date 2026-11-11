package com.pethome.service.impl;

import com.pethome.config.UploadProperties;
import com.pethome.service.FileStorageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 对象存储实现占位（storage-type=oss 时启用）。
 *
 * <p>当前版本未接入具体云厂商 SDK。刻意做成「启动即失败」而不是静默降级到本地磁盘：
 * 生产环境若误配为 oss 却仍写本地盘，容器重启后图片全部丢失且难以发现。</p>
 *
 * <p>接入方式：引入对应 SDK（如 aliyun-sdk-oss / minio），实现 {@link #storeImage}，
 * 其余业务代码无需改动。</p>
 *
 * @author pet-home
 */
@Service
@ConditionalOnProperty(name = "pethome.upload.storage-type", havingValue = "oss")
public class ObjectStorageFileStorageService implements FileStorageService {

    public ObjectStorageFileStorageService(UploadProperties uploadProperties) {
        UploadProperties.Oss oss = uploadProperties.getOss();
        if (oss == null || StringUtils.isAnyBlank(oss.getEndpoint(), oss.getBucket(),
                oss.getAccessKey(), oss.getSecretKey())) {
            throw new IllegalStateException(
                    "storage-type=oss 但对象存储配置不完整，请配置 pethome.upload.oss.endpoint/bucket/access-key/secret-key");
        }
        throw new IllegalStateException(
                "storage-type=oss 尚未接入对象存储 SDK，请改用 storage-type=local 或先完成 ObjectStorageFileStorageService 实现");
    }

    @Override
    public String storeImage(MultipartFile file) {
        throw new UnsupportedOperationException("对象存储尚未接入");
    }

}
