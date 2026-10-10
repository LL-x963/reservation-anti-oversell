package com.pethome.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * C 端用户登录请求
 *
 * @author pet-home
 */
@Data
public class UserLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 微信 openid，首次登录自动注册 */
    @NotBlank(message = "openid不能为空")
    @Size(max = 64, message = "openid长度不能超过64")
    private String openid;

}
