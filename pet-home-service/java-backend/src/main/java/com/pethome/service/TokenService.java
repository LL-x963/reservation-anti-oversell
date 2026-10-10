package com.pethome.service;

import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.enums.UserType;
import com.pethome.config.JwtProperties;
import com.pethome.util.JwtUtil;
import com.pethome.util.RedisUtil;
import com.pethome.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 登录 token 管理：签发 + Redis 白名单维护 + 注销
 *
 * @author pet-home
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenService {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;
    private final JwtProperties jwtProperties;

    /**
     * 签发 token 并写入 Redis 白名单，有效期与 JWT 保持一致
     *
     * @param name 登录人姓名，随登录响应一并返回给前端
     */
    public LoginVO issue(Long userId, UserType userType, String name) {
        String token = jwtUtil.generateToken(userId, userType);
        redisUtil.set(RedisKeyConstant.loginTokenKey(userType, userId), token,
                jwtProperties.getExpireSeconds(), TimeUnit.SECONDS);
        log.info("签发登录 token: userType={}, userId={}", userType, userId);
        return new LoginVO(token, userId, userType.getCode(), jwtProperties.getExpireSeconds(), name);
    }

    /**
     * 注销：移除 Redis 白名单，token 立即失效
     */
    public void revoke(Long userId, UserType userType) {
        redisUtil.delete(RedisKeyConstant.loginTokenKey(userType, userId));
        log.info("注销登录 token: userType={}, userId={}", userType, userId);
    }

}
