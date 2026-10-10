package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.dto.BeauticianAddRequest;
import com.pethome.dto.BeauticianPageQuery;
import com.pethome.dto.BeauticianUpdateRequest;
import com.pethome.service.BeauticianService;
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
 * 管理端 - 美容师管理
 *
 * <p>路径前缀 /api/admin/** 需要管理端员工 token（由 AuthInterceptor 校验）。</p>
 *
 * @author pet-home
 */
@Validated
@RestController
@RequestMapping("/api/admin/beautician")
@RequiredArgsConstructor
public class AdminBeauticianController {

    private final BeauticianService beauticianService;

    /**
     * 分页查询，支持按姓名、技能标签、状态筛选
     */
    @GetMapping("/page")
    public Result page(@Valid BeauticianPageQuery query) {
        return Result.success(beauticianService.pageForAdmin(query));
    }

    /**
     * 查询所有在职美容师列表
     */
    @GetMapping("/list")
    public Result list() {
        return Result.success(beauticianService.listOnDuty());
    }

    /**
     * 新增美容师
     */
    @PostMapping("/add")
    public Result add(@RequestBody @Valid BeauticianAddRequest request) {
        return Result.success(beauticianService.add(request));
    }

    /**
     * 修改美容师信息
     */
    @PutMapping("/update")
    public Result update(@RequestBody @Valid BeauticianUpdateRequest request) {
        beauticianService.update(request);
        return Result.success();
    }

    /**
     * 删除美容师
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable @Min(value = 1, message = "美容师id不合法") Long id) {
        beauticianService.delete(id);
        return Result.success();
    }

}
