package com.pethome.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端员工登录请求
 *
 * @author pet-home
 */
@Data
public class EmployeeLoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户名 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50")
    private String username;

    /** 密码（明文，服务端用 BCrypt 校验） */
    @NotBlank(message = "密码不能为空")
    private String password;

}
