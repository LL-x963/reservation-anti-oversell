package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.service.BeauticianService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 - 美容师
 *
 * <p>路径前缀 /api/user/** 需要 C 端用户 token（由 AuthInterceptor 校验）。</p>
 *
 * @author pet-home
 */
@RestController
@RequestMapping("/api/user/beautician")
@RequiredArgsConstructor
public class UserBeauticianController {

    private final BeauticianService beauticianService;

    /**
     * 在职美容师列表（下单页选择美容师用）
     */
    @GetMapping("/list")
    public Result list() {
        return Result.success(beauticianService.listOnDutyForUser());
    }

}
