package com.pethome.util;

import com.pethome.common.exception.BusinessException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 图片文件头（魔数）校验。
 *
 * <p>仅校验扩展名可以被随意伪造（把可执行/脚本文件改名成 .jpg），
 * 这里读取文件头确认内容确实是允许的图片类型。</p>
 *
 * @author pet-home
 */
public final class ImageMagicValidator {

    private ImageMagicValidator() {
    }

    /**
     * 校验文件头与扩展名一致
     *
     * @param file      上传文件
     * @param extension 已归一化的扩展名（小写，不含点）
     */
    public static void validate(MultipartFile file, String extension) {
        byte[] header = readHeader(file, 12);
        boolean matched = switch (extension) {
            case "jpg", "jpeg" -> isJpeg(header);
            case "png" -> isPng(header);
            case "gif" -> isGif(header);
            case "webp" -> isWebp(header);
            default -> false;
        };
        if (!matched) {
            throw new BusinessException("文件内容与图片格式不符，请上传真实的图片文件");
        }
    }

    private static byte[] readHeader(MultipartFile file, int length) {
        byte[] buffer = new byte[length];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(buffer, 0, length);
            if (read < 4) {
                throw new BusinessException("文件内容为空或不是有效图片");
            }
        } catch (IOException e) {
            throw new BusinessException("读取上传文件失败，请稍后重试");
        }
        return buffer;
    }

    /** JPEG: FF D8 FF */
    private static boolean isJpeg(byte[] h) {
        return (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
    }

    /** PNG: 89 50 4E 47 0D 0A 1A 0A */
    private static boolean isPng(byte[] h) {
        return (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G'
                && (h[4] & 0xFF) == 0x0D && (h[5] & 0xFF) == 0x0A && (h[6] & 0xFF) == 0x1A && (h[7] & 0xFF) == 0x0A;
    }

    /** GIF: "GIF87a" / "GIF89a" */
    private static boolean isGif(byte[] h) {
        return h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8'
                && (h[4] == '7' || h[4] == '9') && h[5] == 'a';
    }

    /** WEBP: "RIFF" + 4 字节长度 + "WEBP" */
    private static boolean isWebp(byte[] h) {
        return h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }

}
