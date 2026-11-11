package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.common.annotation.RequireRole;
import com.pethome.dto.OrderAdminPageQuery;
import com.pethome.dto.OrderAssignRequest;
import com.pethome.service.AdminOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 - 预约订单
 *
 * <p>路径前缀 /api/admin/** 需要管理端员工 token（由 AuthInterceptor 校验）。</p>
 *
 * @author pet-home
 */
@Validated
@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    /**
     * 分页查询，支持按状态、预约日期、美容师筛选
     */
    @GetMapping("/page")
    public Result page(@Valid OrderAdminPageQuery query) {
        return Result.success(adminOrderService.pageForAdmin(query));
    }

    /**
     * 订单详情
     */
    @GetMapping("/{id}")
    public Result detail(@PathVariable @Min(value = 1, message = "订单id不合法") Long id) {
        return Result.success(adminOrderService.detailForAdmin(id));
    }

    /**
     * 确认订单
     */
    @PutMapping("/confirm/{id}")
    public Result confirm(@PathVariable @Min(value = 1, message = "订单id不合法") Long id) {
        adminOrderService.confirm(id);
        return Result.success();
    }

    /**
     * 分配/改派美容师（涉及排班资源调整，仅管理员）
     */
    @PutMapping("/assign")
    @RequireRole("ADMIN")
    public Result assign(@RequestBody @Valid OrderAssignRequest request) {
        adminOrderService.assign(request);
        return Result.success();
    }

    /**
     * 取消订单（影响排班释放，仅管理员）
     */
    @PutMapping("/cancel/{id}")
    @RequireRole("ADMIN")
    public Result cancel(@PathVariable @Min(value = 1, message = "订单id不合法") Long id) {
        adminOrderService.cancelByAdmin(id);
        return Result.success();
    }

    /**
     * 完成订单
     */
    @PutMapping("/complete/{id}")
    public Result complete(@PathVariable @Min(value = 1, message = "订单id不合法") Long id) {
        adminOrderService.complete(id);
        return Result.success();
    }

}
