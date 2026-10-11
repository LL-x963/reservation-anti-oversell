package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.service.AddressBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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

}
