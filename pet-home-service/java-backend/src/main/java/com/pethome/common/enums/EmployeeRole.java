package com.pethome.common.enums;

/**
 * 管理端员工角色
 *
 * <p>ADMIN：全部权限；OPERATOR：日常运营权限，不能取消订单、改派美容师、删除知识库等高影响操作。</p>
 *
 * @author pet-home
 */
public enum EmployeeRole {

    /** 管理员 */
    ADMIN,

    /** 运营人员 */
    OPERATOR;

    /**
     * 解析角色，未知或为空时按最小权限返回 OPERATOR
     */
    public static EmployeeRole fromCode(String role) {
        if (role != null) {
            for (EmployeeRole value : values()) {
                if (value.name().equalsIgnoreCase(role.trim())) {
                    return value;
                }
            }
        }
        return OPERATOR;
    }

}
