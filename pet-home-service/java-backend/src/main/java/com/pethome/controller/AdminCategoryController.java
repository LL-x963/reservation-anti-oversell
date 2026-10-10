package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.dto.ServiceCategoryAddRequest;
import com.pethome.dto.ServiceCategoryUpdateRequest;
import com.pethome.service.ServiceCategoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 - 服务分类管理
 *
 * <p>路径前缀 /api/admin/** 需要管理端员工 token（由 AuthInterceptor 校验）。</p>
 *
 * @author pet-home
 */
@Validated
@RestController
@RequestMapping("/api/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final ServiceCategoryService serviceCategoryService;

    /**
     * 查询所有分类列表（含禁用）
     */
    @GetMapping("/list")
    public Result list() {
        return Result.success(serviceCategoryService.listAllForAdmin());
    }

    /**
     * 新增分类
     */
    @PostMapping("/add")
    public Result add(@RequestBody @Valid ServiceCategoryAddRequest request) {
        return Result.success(serviceCategoryService.add(request));
    }

    /**
     * 修改分类
     */
    @PutMapping("/update")
    public Result update(@RequestBody @Valid ServiceCategoryUpdateRequest request) {
        serviceCategoryService.update(request);
        return Result.success();
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable @Min(value = 1, message = "分类id不合法") Long id) {
        serviceCategoryService.delete(id);
        return Result.success();
    }

}
