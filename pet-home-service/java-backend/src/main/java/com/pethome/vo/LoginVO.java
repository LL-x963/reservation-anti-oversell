package com.pethome.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 登录结果
 *
 * @author pet-home
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** JWT token，请求时放入请求头 Authorization，格式 Bearer {token} */
    private String token;

    /** 登录用户 id */
    private Long userId;

    /** 登录身份类型 1 C端用户 2 管理端员工 */
    private Integer userType;

    /** 有效期（秒） */
    private Long expireSeconds;

    /** 登录人姓名（管理端取员工姓名、C 端取用户姓名），前端登录后可直接展示，无需再查一次 */
    private String name;

}
