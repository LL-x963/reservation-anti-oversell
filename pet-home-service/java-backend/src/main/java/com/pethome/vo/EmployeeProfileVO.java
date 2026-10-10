package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理端员工信息（不含密码）
 *
 * @author pet-home
 */
@Data
public class EmployeeProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String name;

    private String phone;

    /** 性别 0未知 1男 2女 */
    private Integer sex;

    private String avatar;

    /** 状态 1启用 0禁用 */
    private Integer status;

}
