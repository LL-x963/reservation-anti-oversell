package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.service.ServiceCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 - 服务分类
 *
 * <p>路径前缀 /api/user/** 需要 C 端用户 token（由 AuthInterceptor 校验）。</p>
 *
 * @author pet-home
 */
@RestController
@RequestMapping("/api/user/category")
@RequiredArgsConstructor
public class UserCategoryController {

    private final ServiceCategoryService serviceCategoryService;

    /**
     * 查询所有启用的分类列表
     */
    @GetMapping("/list")
    public Result list() {
        return Result.success(serviceCategoryService.listEnabledForUser());
    }

}
