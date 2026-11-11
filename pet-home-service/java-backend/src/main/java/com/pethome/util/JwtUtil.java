package com.pethome.util;

import com.pethome.common.enums.UserType;
import com.pethome.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 *
 * @author pet-home
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    /** 身份类型 claim 名称 */
    public static final String CLAIM_USER_TYPE = "userType";

    /** 员工角色 claim 名称（仅管理端 token 携带） */
    public static final String CLAIM_ROLE = "role";

    private final JwtProperties jwtProperties;

    /**
     * 生成 token，默认 C 端用户身份、默认有效期（24 小时）
     */
    public String generateToken(Long userId) {
        return generateToken(userId, UserType.USER);
    }

    /**
     * 生成 token，默认有效期（24 小时）
     */
    public String generateToken(Long userId, UserType userType) {
        return generateToken(userId, userType, jwtProperties.getExpireSeconds());
    }

    /**
     * 生成 token
     *
     * @param userId        用户 id
     * @param userType      登录身份类型
     * @param expireSeconds 有效期（秒）
     */
    public String generateToken(Long userId, UserType userType, long expireSeconds) {
        return generateToken(userId, userType, expireSeconds, null);
    }

    /**
     * 生成 token，可携带管理端员工角色
     *
     * @param role 员工角色（ADMIN / OPERATOR），C 端用户传 null
     */
    public String generateToken(Long userId, UserType userType, long expireSeconds, String role) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_TYPE, userType.getCode())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireSeconds * 1000L))
                .signWith(secretKey());
        if (StringUtils.isNotBlank(role)) {
            builder.claim(CLAIM_ROLE, role);
        }
        return builder.compact();
    }

    /**
     * 解析 token 获取用户 id
     */
    public Long parseUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    /**
     * 解析 token 获取登录身份类型
     */
    public UserType parseUserType(String token) {
        Integer typeCode = parseClaims(token).get(CLAIM_USER_TYPE, Integer.class);
        if (typeCode == null) {
            throw new JwtException("token 中缺少身份信息");
        }
        return UserType.fromCode(typeCode);
    }

    /**
     * 解析 token 中的员工角色，C 端 token 或未携带角色时返回 null
     */
    public String parseRole(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    /**
     * 校验 token 是否有效（签名正确且未过期）
     */
    public boolean validateToken(String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("token 校验失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取 token 过期时间
     */
    public Date getExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    /**
     * 从请求头值中提取 token，自动去除 Bearer 前缀
     */
    public String resolveToken(String headerValue) {
        if (StringUtils.isBlank(headerValue)) {
            return null;
        }
        String prefix = jwtProperties.getTokenPrefix();
        if (StringUtils.isNotBlank(prefix) && headerValue.startsWith(prefix)) {
            return headerValue.substring(prefix.length()).trim();
        }
        return headerValue.trim();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

}
