package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.dto.OrderCommentRequest;
import com.pethome.dto.OrderPageQuery;
import com.pethome.dto.OrderPayRequest;
import com.pethome.dto.OrderSubmitRequest;
import com.pethome.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 - 预约订单
 *
 * <p>路径前缀 /api/user/** 需要 C 端用户 token（由 AuthInterceptor 校验），
 * 操作对象由拦截器写入 ThreadLocal 后由 Service 层自动绑定为当前登录用户。</p>
 *
 * @author pet-home
 */
@Validated
@RestController
@RequestMapping("/api/user/order")
@RequiredArgsConstructor
public class UserOrderController {

    private final OrderService orderService;

    /**
     * 提交预约
     */
    @PostMapping("/submit")
    public Result submit(@RequestBody @Valid OrderSubmitRequest request) {
        return Result.success(orderService.submit(request));
    }

    /**
     * 模拟支付
     */
    @PostMapping("/pay")
    public Result pay(@RequestBody @Valid OrderPayRequest request) {
        orderService.pay(request);
        return Result.success();
    }

    /**
     * 分页查询我的订单，支持按状态筛选
     */
    @GetMapping("/page")
    public Result page(@Valid OrderPageQuery query) {
        return Result.success(orderService.pageMyOrders(query));
    }

    /**
     * 订单详情
     */
    @GetMapping("/{id}")
    public Result detail(@PathVariable @Min(value = 1, message = "订单id不合法") Long id) {
        return Result.success(orderService.detailForUser(id));
    }

    /**
     * 取消订单
     */
    @PutMapping("/cancel/{id}")
    public Result cancel(@PathVariable @Min(value = 1, message = "订单id不合法") Long id) {
        orderService.cancelByUser(id);
        return Result.success();
    }

    /**
     * 催单
     */
    @PostMapping("/reminder/{id}")
    public Result reminder(@PathVariable @Min(value = 1, message = "订单id不合法") Long id) {
        orderService.remind(id);
        return Result.success();
    }

    /**
     * 评价订单
     */
    @PostMapping("/comment")
    public Result comment(@RequestBody @Valid OrderCommentRequest request) {
        orderService.comment(request);
        return Result.success();
    }

}
