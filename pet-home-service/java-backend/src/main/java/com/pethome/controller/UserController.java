package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.common.context.UserContext;
import com.pethome.dto.UserLoginRequest;
import com.pethome.dto.UserRegisterRequest;
import com.pethome.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端用户接口
 *
 * <p>/api/user/login、/api/user/register 免登录，其余 /api/user/** 需要 C 端用户 token。</p>
 *
 * @author pet-home
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 用户登录（免登录接口）
     *
     * <p>支持三种方式（三选一）：{@code {code}} 微信小程序登录、
     * {@code {phone, password}} 手机号密码登录、{@code {openid}} 仅开发环境直登。</p>
     */
    @PostMapping("/login")
    public Result login(@RequestBody @Valid UserLoginRequest request) {
        return Result.success(userService.login(request));
    }

    /**
     * 用户注册（免登录接口）
     *
     * <p>手机号 + 密码注册，密码需 8~32 位且同时包含字母和数字。</p>
     */
    @PostMapping("/register")
    public Result register(@RequestBody @Valid UserRegisterRequest request) {
        userService.register(request);
        return Result.success();
    }

    /**
     * 获取当前登录用户信息（需登录）
     */
    @GetMapping("/profile")
    public Result profile() {
        return Result.success(userService.getProfile(UserContext.requireUserId()));
    }

    /**
     * 退出登录（需登录）
     */
    @PostMapping("/logout")
    public Result logout() {
        userService.logout(UserContext.requireUserId());
        return Result.success();
    }

}
