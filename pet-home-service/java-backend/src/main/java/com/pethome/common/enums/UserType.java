package com.pethome.common.enums;

import lombok.Getter;

/**
 * 登录身份类型
 *
 * @author pet-home
 */
@Getter
public enum UserType {

    /** C 端用户，接口前缀 /api/user/** */
    USER(1, "C端用户"),

    /** 管理端员工，接口前缀 /api/admin/** */
    EMPLOYEE(2, "管理端员工");

    private final int code;
    private final String desc;

    UserType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UserType fromCode(int code) {
        for (UserType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的登录身份类型: " + code);
    }

}
