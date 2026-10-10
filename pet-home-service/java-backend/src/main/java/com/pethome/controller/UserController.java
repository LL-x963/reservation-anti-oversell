package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.common.context.UserContext;
import com.pethome.dto.UserLoginRequest;
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
 * <p>/api/user/login 免登录，其余 /api/user/** 需要 C 端用户 token。</p>
 *
 * @author pet-home
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * openid 登录（免登录接口）
     */
    @PostMapping("/login")
    public Result login(@RequestBody @Valid UserLoginRequest request) {
        return Result.success(userService.login(request.getOpenid()));
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
