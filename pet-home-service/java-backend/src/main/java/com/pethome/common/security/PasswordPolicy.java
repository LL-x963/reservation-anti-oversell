package com.pethome.common.security;

import com.pethome.common.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * 密码强度策略（仅用于注册与修改密码，登录时不做强度校验，避免老账号无法登录）。
 *
 * @author pet-home
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;

    private static final int MAX_LENGTH = 32;

    /** 常见弱口令，禁止使用 */
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "12345678", "123456789", "password", "password1", "qwerty123",
            "abc12345", "11111111", "88888888", "admin123", "iloveyou");

    private PasswordPolicy() {
    }

    /**
     * 校验密码强度，不合规直接抛业务异常
     *
     * @param password 明文密码
     */
    public static void validate(String password) {
        if (StringUtils.isBlank(password) || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new BusinessException("密码长度需为 " + MIN_LENGTH + "~" + MAX_LENGTH + " 位");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (char c : password.toCharArray()) {
            if (Character.isLetter(c)) {
                hasLetter = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        if (!hasLetter || !hasDigit) {
            throw new BusinessException("密码需同时包含字母和数字");
        }
        if (WEAK_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
            throw new BusinessException("密码过于简单，请更换更复杂的密码");
        }
    }

}
