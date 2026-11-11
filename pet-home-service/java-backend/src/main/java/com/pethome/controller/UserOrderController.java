package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.dto.OrderCommentRequest;
import com.pethome.dto.OrderPageQuery;
import com.pethome.dto.OrderPayRequest;
import com.pethome.dto.OrderSubmitRequest;
import com.pethome.service.OrderService;
import com.pethome.service.PaymentService;
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
    private final PaymentService paymentService;

    /**
     * 提交预约
     */
    @PostMapping("/submit")
    public Result submit(@RequestBody @Valid OrderSubmitRequest request) {
        return Result.success(orderService.submit(request));
    }

    /**
     * 发起支付
     *
     * <p>模拟渠道会同步完成支付并返回 mock=true；接入真实渠道后返回唤起支付所需参数，
     * 订单在收到渠道回调并验签通过后才变为待确认。</p>
     */
    @PostMapping("/pay")
    public Result pay(@RequestBody @Valid OrderPayRequest request) {
        return Result.success(paymentService.pay(request.getOrderId()));
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
     * 删除已取消订单（仅本人）
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable @Min(value = 1, message = "订单id不合法") Long id) {
        orderService.deleteByUser(id);
        return Result.success();
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
