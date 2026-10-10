package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * C 端用户信息
 *
 * @author pet-home
 */
@Data
public class UserProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String phone;

    /** 性别 0未知 1男 2女 */
    private Integer sex;

    private String avatar;

}
