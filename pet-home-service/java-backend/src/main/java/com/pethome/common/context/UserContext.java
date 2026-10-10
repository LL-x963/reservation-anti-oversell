package com.pethome.common.context;

import com.pethome.common.enums.UserType;
import com.pethome.common.exception.BusinessException;

/**
 * 当前登录用户上下文（基于 ThreadLocal）
 *
 * <p>由登录拦截器在请求进入时写入、请求结束时清理，业务层通过本类获取当前登录用户。</p>
 *
 * @author pet-home
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<UserType> USER_TYPE = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, UserType userType) {
        USER_ID.set(userId);
        USER_TYPE.set(userType);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static UserType getUserType() {
        return USER_TYPE.get();
    }

    /**
     * 获取当前登录用户 id，未登录则抛出业务异常
     */
    public static Long requireUserId() {
        Long userId = USER_ID.get();
        if (userId == null) {
            throw new BusinessException(401, "未获取到登录用户，请先登录");
        }
        return userId;
    }

    public static void clear() {
        USER_ID.remove();
        USER_TYPE.remove();
    }

}
