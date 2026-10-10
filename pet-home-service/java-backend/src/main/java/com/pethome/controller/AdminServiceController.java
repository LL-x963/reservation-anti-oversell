package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.dto.ServiceItemAddRequest;
import com.pethome.dto.ServiceItemPageQuery;
import com.pethome.dto.ServiceItemStatusRequest;
import com.pethome.dto.ServiceItemUpdateRequest;
import com.pethome.service.ServiceItemService;
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
 * 管理端 - 服务项目管理
 *
 * <p>路径前缀 /api/admin/** 需要管理端员工 token（由 AuthInterceptor 校验）。</p>
 *
 * @author pet-home
 */
@Validated
@RestController
@RequestMapping("/api/admin/service")
@RequiredArgsConstructor
public class AdminServiceController {

    private final ServiceItemService serviceItemService;

    /**
     * 分页查询，支持按分类id、名称、状态筛选
     */
    @GetMapping("/page")
    public Result page(@Valid ServiceItemPageQuery query) {
        return Result.success(serviceItemService.pageForAdmin(query));
    }

    /**
     * 新增服务项目
     */
    @PostMapping("/add")
    public Result add(@RequestBody @Valid ServiceItemAddRequest request) {
        return Result.success(serviceItemService.add(request));
    }

    /**
     * 修改服务项目
     */
    @PutMapping("/update")
    public Result update(@RequestBody @Valid ServiceItemUpdateRequest request) {
        serviceItemService.update(request);
        return Result.success();
    }

    /**
     * 服务项目上下架
     *
     * @param id 服务项目 id
     */
    @PutMapping("/status/{id}")
    public Result updateStatus(@PathVariable @Min(value = 1, message = "服务项目id不合法") Long id,
                               @RequestBody @Valid ServiceItemStatusRequest request) {
        serviceItemService.updateStatus(id, request.getStatus());
        return Result.success();
    }

    /**
     * 删除服务项目
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable @Min(value = 1, message = "服务项目id不合法") Long id) {
        serviceItemService.delete(id);
        return Result.success();
    }

}
