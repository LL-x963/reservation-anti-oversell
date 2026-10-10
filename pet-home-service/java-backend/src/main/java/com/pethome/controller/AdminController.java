package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.common.context.UserContext;
import com.pethome.dto.EmployeeLoginRequest;
import com.pethome.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端接口
 *
 * <p>/api/admin/login 免登录，其余 /api/admin/** 需要员工 token。</p>
 *
 * @author pet-home
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final EmployeeService employeeService;

    /**
     * 员工账号密码登录（免登录接口）
     *
     * <p>同时兼容两个路径：{@code /api/admin/login} 与 {@code /api/admin/employee/login}。</p>
     */
    @PostMapping({"/login", "/employee/login"})
    public Result login(@RequestBody @Valid EmployeeLoginRequest request) {
        return Result.success(employeeService.login(request));
    }

    /**
     * 获取当前登录员工信息（需登录）
     */
    @GetMapping("/profile")
    public Result profile() {
        return Result.success(employeeService.getProfile(UserContext.requireUserId()));
    }

    /**
     * 退出登录（需登录）
     */
    @PostMapping("/logout")
    public Result logout() {
        employeeService.logout(UserContext.requireUserId());
        return Result.success();
    }

}
