package com.pethome.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * C 端用户登录请求
 *
 * <p>支持两种登录方式，二选一（由 UserServiceImpl 校验互斥）：</p>
 * <ul>
 *   <li>微信登录：只传 {@code openid}，首次登录自动注册；</li>
 *   <li>手机号密码登录：传 {@code phone} + {@code password}，手机号未注册时自动注册（密码即注册密码）。</li>
 * </ul>
 *
 * @author pet-home
 */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 微信 openid，首次登录自动注册；与「手机号 + 密码」二选一 */
    @Size(max = 64, message = "openid长度不能超过64")
    private String openid;

    /** 手机号，需与密码同时传递；与 openid 二选一 */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 密码（明文，服务端用 BCrypt 校验/存储），手机号登录时必填 */
    @Size(min = 6, max = 20, message = "密码长度需为 6~20 位")
    private String password;

}
