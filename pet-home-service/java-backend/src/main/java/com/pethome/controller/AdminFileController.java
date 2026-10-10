package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理端 - 文件上传
 *
 * <p>路径前缀 /api/admin/** 需要管理端员工 token（由 AuthInterceptor 校验）。</p>
 *
 * @author pet-home
 */
@RestController
@RequestMapping("/api/admin/file")
@RequiredArgsConstructor
public class AdminFileController {

    private final FileStorageService fileStorageService;

    /**
     * 图片上传，返回可直接访问的图片地址，供服务项目 image 字段使用
     *
     * @param file 表单字段名固定为 file
     */
    @PostMapping("/upload")
    public Result upload(@RequestParam("file") MultipartFile file) {
        return Result.success(fileStorageService.storeImage(file));
    }

}
