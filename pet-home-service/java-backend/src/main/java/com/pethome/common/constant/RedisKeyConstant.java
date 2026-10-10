package com.pethome.common.constant;

import com.pethome.common.enums.UserType;

/**
 * Redis key 常量
 *
 * @author pet-home
 */
public final class RedisKeyConstant {

    private RedisKeyConstant() {
    }

    /** 统一业务前缀 */
    public static final String PREFIX = "pethome:";

    /** 登录 token 白名单前缀，完整形如 pethome:login:token:1:100 */
    public static final String LOGIN_TOKEN_PREFIX = PREFIX + "login:token:";

    /**
     * 构建登录 token 白名单 key
     *
     * @param userType 登录身份类型
     * @param userId   用户 id
     */
    public static String loginTokenKey(UserType userType, Long userId) {
        return LOGIN_TOKEN_PREFIX + userType.getCode() + ":" + userId;
    }

}
