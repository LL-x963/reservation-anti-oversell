package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.dto.ServiceItemUserPageQuery;
import com.pethome.service.ServiceItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 - 服务项目
 *
 * <p>路径前缀 /api/user/** 需要 C 端用户 token（由 AuthInterceptor 校验）。</p>
 *
 * @author pet-home
 */
@Validated
@RestController
@RequestMapping("/api/user/service")
@RequiredArgsConstructor
public class UserServiceController {

    private final ServiceItemService serviceItemService;

    /**
     * 分页查询上架服务，支持按分类id、宠物类型、价格区间筛选
     */
    @GetMapping("/page")
    public Result page(@Valid ServiceItemUserPageQuery query) {
        return Result.success(serviceItemService.pageForUser(query));
    }

    /**
     * 服务详情（下架不可见）
     */
    @GetMapping("/{id}")
    public Result detail(@PathVariable @Min(value = 1, message = "服务项目id不合法") Long id) {
        return Result.success(serviceItemService.detailForUser(id));
    }

}
