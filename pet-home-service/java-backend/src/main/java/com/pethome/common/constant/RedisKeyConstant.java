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

    /** 预约车 Hash key 前缀，完整形如 cart:100 */
    public static final String CART_PREFIX = "cart:";

    /**
     * 构建预约车 Hash key
     *
     * @param userId 用户 id
     */
    public static String cartKey(Long userId) {
        return CART_PREFIX + userId;
    }

    /** 订单号全局自增序号（单调递增，保证同毫秒内订单号不重复） */
    public static final String ORDER_SEQ_KEY = PREFIX + "order:seq";

    /** 下单幂等 key 前缀，完整形如 pethome:order:idem:100:6f1c... */
    public static final String ORDER_IDEMPOTENT_PREFIX = PREFIX + "order:idem:";

    /**
     * 构建下单幂等 key
     *
     * @param userId    用户 id
     * @param requestId 客户端生成的请求唯一标识
     */
    public static String orderIdempotentKey(Long userId, String requestId) {
        return ORDER_IDEMPOTENT_PREFIX + userId + ":" + requestId;
    }

    /** 登录失败计数 key 前缀，完整形如 pethome:login:fail:user:13800138000 */
    public static final String LOGIN_FAIL_PREFIX = PREFIX + "login:fail:";

    /** 登录 / 注册接口限流 key 前缀，完整形如 pethome:login:rate:127.0.0.1 */
    public static final String LOGIN_RATE_PREFIX = PREFIX + "login:rate:";

    /**
     * 构建登录失败计数 key
     *
     * @param accountKey 带身份前缀的账号标识，如 user:13800138000、employee:admin
     */
    public static String loginFailKey(String accountKey) {
        return LOGIN_FAIL_PREFIX + accountKey;
    }

    /**
     * 构建登录接口限流 key
     *
     * @param clientIp 来源 IP
     */
    public static String loginRateKey(String clientIp) {
        return LOGIN_RATE_PREFIX + clientIp;
    }

    /** 催单限流 key 前缀，完整形如 reminder:100:1 */
    public static final String REMINDER_PREFIX = "reminder:";

    /**
     * 构建催单限流 key
     *
     * @param orderId 订单 id
     * @param userId  用户 id
     */
    public static String reminderKey(Long orderId, Long userId) {
        return REMINDER_PREFIX + orderId + ":" + userId;
    }

}
