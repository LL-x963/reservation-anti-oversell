package com.pethome.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * C 端用户注册请求
 *
 * @author pet-home
 */
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 手机号 */
    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 密码：8~32 位且同时包含字母和数字（由 PasswordPolicy 进一步校验） */
    @NotBlank(message = "请输入密码")
    @Size(min = 8, max = 32, message = "密码长度需为 8~32 位")
    private String password;

    /** 昵称，选填 */
    @Size(max = 64, message = "昵称长度不能超过 64")
    private String name;

}
