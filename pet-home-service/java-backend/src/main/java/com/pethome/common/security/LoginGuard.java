package com.pethome.common.security;

import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.exception.BusinessException;
import com.pethome.config.AuthProperties;
import com.pethome.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 登录失败锁定。
 *
 * <p>按「登录账号输入值」计数（不区分账号是否存在），连续失败达到阈值后锁定一段时间，
 * 用于抵御撞库与暴力破解。成功登录后清空计数。</p>
 *
 * @author pet-home
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginGuard {

    private final RedisUtil redisUtil;

    private final AuthProperties authProperties;

    /**
     * 登录前检查是否处于锁定期
     *
     * @param accountKey 账号标识，调用方需带身份前缀，如 user:13800138000 / employee:admin
     */
    public void checkAccountLocked(String accountKey) {
        String countText = redisUtil.getString(RedisKeyConstant.loginFailKey(accountKey));
        if (countText == null) {
            return;
        }
        long count;
        try {
            count = Long.parseLong(countText);
        } catch (NumberFormatException e) {
            // 脏数据（例如历史版本写入的 JSON）直接清理，避免把用户永久锁死
            redisUtil.delete(RedisKeyConstant.loginFailKey(accountKey));
            return;
        }
        if (count >= authProperties.getMaxFailCount()) {
            long ttl = redisUtil.getExpire(RedisKeyConstant.loginFailKey(accountKey));
            long minutes = ttl > 0 ? Math.max(1, ttl / 60) : authProperties.getLockMinutes();
            throw new BusinessException("登录失败次数过多，请 " + minutes + " 分钟后再试");
        }
    }

    /**
     * 记录一次登录失败，达到阈值时直接抛出锁定提示
     */
    public void onLoginFailure(String accountKey) {
        String key = RedisKeyConstant.loginFailKey(accountKey);
        long count = redisUtil.increment(key, 1);
        if (count == 1) {
            redisUtil.expire(key, authProperties.getLockMinutes(), TimeUnit.MINUTES);
        }
        if (count >= authProperties.getMaxFailCount()) {
            log.warn("登录失败次数达到阈值，已锁定: accountKey={}, count={}", accountKey, count);
            throw new BusinessException("登录失败次数过多，请 " + authProperties.getLockMinutes() + " 分钟后再试");
        }
    }

    /**
     * 登录成功后清空失败计数
     */
    public void onLoginSuccess(String accountKey) {
        redisUtil.delete(RedisKeyConstant.loginFailKey(accountKey));
    }

}
