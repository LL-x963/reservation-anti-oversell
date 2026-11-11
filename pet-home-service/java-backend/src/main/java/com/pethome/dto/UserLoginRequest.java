package com.pethome.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * C 端用户登录请求
 *
 * <p>支持三种登录方式，由 UserServiceImpl 校验互斥：</p>
 * <ul>
 *   <li>微信小程序登录：传 {@code code}，由服务端调 code2session 换取 openid（推荐，生产可用）；</li>
 *   <li>手机号密码登录：传 {@code phone} + {@code password}，手机号必须已注册，不再自动注册；</li>
 *   <li>{@code openid} 直登：仅本地开发与自动化测试可用，生产禁用。</li>
 * </ul>
 *
 * @author pet-home
 */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 微信小程序登录 code（wx.login 获取的一次性凭证），与手机号密码二选一 */
    @Size(max = 128, message = "code长度不能超过128")
    private String code;

    /**
     * 微信 openid。
     *
     * <p>仅本地开发 / 自动化测试可用，生产环境该入口被禁用——openid 由客户端直传即可伪造身份。</p>
     */
    @Size(max = 64, message = "openid长度不能超过64")
    private String openid;

    /** 手机号，需与密码同时传递；与微信登录二选一 */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 密码（明文，服务端用 BCrypt 校验），手机号登录时必填 */
    @Size(min = 6, max = 32, message = "密码长度需为 6~32 位")
    private String password;

}
