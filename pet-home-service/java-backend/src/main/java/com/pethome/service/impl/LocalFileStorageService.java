package com.pethome.service.impl;

import com.pethome.common.exception.BusinessException;
import com.pethome.config.UploadProperties;
import com.pethome.service.FileStorageService;
import com.pethome.util.ImageMagicValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地磁盘文件存储实现
 *
 * <p>文件按 yyyy/MM/dd 分目录存放，返回可直接访问的相对 URL（由 WebMvcConfig 中的静态资源映射提供访问）。</p>
 *
 * @author pet-home
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pethome.upload.storage-type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private static final DateTimeFormatter DATE_DIR_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final UploadProperties uploadProperties;

    @Override
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        if (file.getSize() > uploadProperties.getMaxSize()) {
            throw new BusinessException("文件大小不能超过 " + toMb(uploadProperties.getMaxSize()) + "MB");
        }

        String extension = resolveExtension(file.getOriginalFilename());
        if (!uploadProperties.getAllowedExtensions().contains(extension)) {
            throw new BusinessException("仅支持上传 " + String.join("/", uploadProperties.getAllowedExtensions()) + " 格式的图片");
        }
        // 扩展名可以伪造，必须校验文件头（魔数）确认是真实图片
        ImageMagicValidator.validate(file, extension);

        String dateDir = LocalDate.now().format(DATE_DIR_FORMATTER);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        Path baseDir = Paths.get(uploadProperties.getBaseDir()).toAbsolutePath().normalize();
        Path targetDir = baseDir.resolve(dateDir).normalize();
        // 防止路径穿越
        if (!targetDir.startsWith(baseDir)) {
            throw new BusinessException("文件存储路径不合法");
        }

        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetDir.resolve(fileName).toFile());
        } catch (IOException e) {
            log.error("文件保存失败: baseDir={}, dateDir={}", baseDir, dateDir, e);
            throw new BusinessException("文件上传失败，请稍后重试");
        }

        String url = uploadProperties.normalizedUrlPrefix() + dateDir + "/" + fileName;
        log.info("文件上传成功: url={}", url);
        return url;
    }

    private String resolveExtension(String originalFilename) {
        if (StringUtils.isBlank(originalFilename)) {
            throw new BusinessException("上传文件名称不能为空");
        }
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == originalFilename.length() - 1) {
            throw new BusinessException("上传文件缺少扩展名");
        }
        return originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private long toMb(long bytes) {
        return bytes / 1024 / 1024;
    }

}
