package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.dto.StatRevenueQuery;
import com.pethome.service.StatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 - 数据统计
 *
 * <p>路径前缀 /api/admin/** 需要管理端员工 token（由 AuthInterceptor 校验）。</p>
 *
 * @author pet-home
 */
@Validated
@RestController
@RequestMapping("/api/admin/stat")
@RequiredArgsConstructor
public class AdminStatController {

    private final StatService statService;

    /**
     * 营业额统计
     *
     * @param query date 基准日期（默认今天）+ type 统计粒度 day/week/month（默认 day）
     */
    @GetMapping("/revenue")
    public Result revenue(@Valid StatRevenueQuery query) {
        return Result.success(statService.revenue(query.getDate(), query.getType()));
    }

    /**
     * 服务销量排行 Top10
     */
    @GetMapping("/serviceRank")
    public Result serviceRank() {
        return Result.success(statService.serviceRank());
    }

    /**
     * 美容师业绩排行
     */
    @GetMapping("/beauticianRank")
    public Result beauticianRank() {
        return Result.success(statService.beauticianRank());
    }

    /**
     * 用户复购率
     */
    @GetMapping("/repurchase")
    public Result repurchase() {
        return Result.success(statService.repurchase());
    }

}
