package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.dto.AddressSaveRequest;
import com.pethome.service.AddressBookService;
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
 * 用户端 - 地址簿
 *
 * <p>路径前缀 /api/user/** 需要 C 端用户 token（由 AuthInterceptor 校验），
 * 地址一律限定在当前登录用户范围内。</p>
 *
 * @author pet-home
 */
@Validated
@RestController
@RequestMapping("/api/user/address")
@RequiredArgsConstructor
public class UserAddressController {

    private final AddressBookService addressBookService;

    /**
     * 当前登录用户的地址列表（下单页选择服务地址用）
     */
    @GetMapping("/list")
    public Result list() {
        return Result.success(addressBookService.listForCurrentUser());
    }

    /**
     * 新增地址
     */
    @PostMapping
    public Result create(@RequestBody @Valid AddressSaveRequest request) {
        return Result.success(addressBookService.create(request));
    }

    /**
     * 修改地址
     */
    @PutMapping("/{id}")
    public Result update(@PathVariable @Min(value = 1, message = "地址id不合法") Long id,
                         @RequestBody @Valid AddressSaveRequest request) {
        addressBookService.update(id, request);
        return Result.success();
    }

    /**
     * 删除地址
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable @Min(value = 1, message = "地址id不合法") Long id) {
        addressBookService.delete(id);
        return Result.success();
    }

    /**
     * 设为默认地址
     */
    @PutMapping("/{id}/default")
    public Result setDefault(@PathVariable @Min(value = 1, message = "地址id不合法") Long id) {
        addressBookService.setDefault(id);
        return Result.success();
    }

}
