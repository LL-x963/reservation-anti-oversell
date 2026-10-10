package com.pethome.util;

import com.pethome.common.enums.UserType;
import com.pethome.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JWT 工具类单元测试
 *
 * @author pet-home
 */
class JwtUtilTest {

    private static final String SECRET = "unit-test-secret-key-must-be-longer-than-32-bytes-0123456789";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret(SECRET);
        jwtProperties.setExpireSeconds(86400L);
        jwtProperties.setHeader("Authorization");
        jwtProperties.setTokenPrefix("Bearer ");
        jwtUtil = new JwtUtil(jwtProperties);
    }

    @Test
    void generateTokenShouldCarryUserIdAndUserType() {
        String token = jwtUtil.generateToken(1001L, UserType.USER);
        assertThat(token).isNotBlank();
        assertThat(jwtUtil.parseUserId(token)).isEqualTo(1001L);
        assertThat(jwtUtil.parseUserType(token)).isEqualTo(UserType.USER);
    }

    @Test
    void generateTokenShouldSupportEmployeeType() {
        String token = jwtUtil.generateToken(2002L, UserType.EMPLOYEE);
        assertThat(jwtUtil.parseUserId(token)).isEqualTo(2002L);
        assertThat(jwtUtil.parseUserType(token)).isEqualTo(UserType.EMPLOYEE);
    }

    @Test
    void generateTokenDefaultShouldBeUserType() {
        String token = jwtUtil.generateToken(3003L);
        assertThat(jwtUtil.parseUserType(token)).isEqualTo(UserType.USER);
    }

    @Test
    void validateTokenShouldReturnTrueForValidToken() {
        String token = jwtUtil.generateToken(1L, UserType.USER);
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    void validateTokenShouldReturnFalseForExpiredToken() {
        String expiredToken = jwtUtil.generateToken(1L, UserType.USER, -10L);
        assertThat(jwtUtil.validateToken(expiredToken)).isFalse();
    }

    @Test
    void validateTokenShouldReturnFalseForBlankOrTamperedToken() {
        assertThat(jwtUtil.validateToken(null)).isFalse();
        assertThat(jwtUtil.validateToken("")).isFalse();
        assertThat(jwtUtil.validateToken("not-a-jwt-token")).isFalse();

        String token = jwtUtil.generateToken(1L, UserType.USER);
        assertThat(jwtUtil.validateToken(token + "tampered")).isFalse();
    }

    @Test
    void expirationShouldBeAboutTwentyFourHours() {
        String token = jwtUtil.generateToken(1L, UserType.USER);
        long ttlMillis = jwtUtil.getExpiration(token).getTime() - System.currentTimeMillis();
        // 允许 1 分钟误差
        assertThat(ttlMillis).isBetween(86400_000L - 60_000L, 86400_000L + 60_000L);
    }

    @Test
    void resolveTokenShouldStripBearerPrefix() {
        assertThat(jwtUtil.resolveToken("Bearer abc.def.ghi")).isEqualTo("abc.def.ghi");
        assertThat(jwtUtil.resolveToken("  abc.def.ghi  ")).isEqualTo("abc.def.ghi");
        assertThat(jwtUtil.resolveToken(null)).isNull();
        assertThat(jwtUtil.resolveToken("  ")).isNull();
    }

}
