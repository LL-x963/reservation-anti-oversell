package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.dto.CartAddRequest;
import com.pethome.dto.CartUpdateRequest;
import com.pethome.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 - 预约车
 *
 * <p>路径前缀 /api/user/** 需要 C 端用户 token（由 AuthInterceptor 校验），
 * 操作对象由拦截器写入 ThreadLocal 后由 Service 层自动绑定为当前登录用户。</p>
 *
 * @author pet-home
 */
@Validated
@RestController
@RequestMapping("/api/user/cart")
@RequiredArgsConstructor
public class UserCartController {

    private final CartService cartService;

    /**
     * 添加服务到预约车
     */
    @PostMapping("/add")
    public Result add(@RequestBody @Valid CartAddRequest request) {
        cartService.add(request);
        return Result.success();
    }

    /**
     * 查询当前用户预约车列表（含服务名称、价格、图片）
     */
    @GetMapping("/list")
    public Result list() {
        return Result.success(cartService.list());
    }

    /**
     * 修改服务数量
     */
    @PutMapping("/update")
    public Result update(@RequestBody @Valid CartUpdateRequest request) {
        cartService.update(request);
        return Result.success();
    }

    /**
     * 删除指定服务
     */
    @DeleteMapping("/delete")
    public Result delete(@RequestParam("serviceId") @NotNull(message = "服务id不能为空")
                         @Min(value = 1, message = "服务id不合法") Long serviceId) {
        cartService.delete(serviceId);
        return Result.success();
    }

    /**
     * 清空预约车
     */
    @DeleteMapping("/clean")
    public Result clean() {
        cartService.clean();
        return Result.success();
    }

}
