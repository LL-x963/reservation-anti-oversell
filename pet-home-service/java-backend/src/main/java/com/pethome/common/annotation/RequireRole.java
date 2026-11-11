package com.pethome.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口所需的管理端角色。
 *
 * <p>可标注在 Controller 类或方法上，方法级优先。未标注表示「登录即可访问」。</p>
 *
 * @author pet-home
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /**
     * 所需角色，取值见 {@link com.pethome.common.enums.EmployeeRole}
     */
    String value();

}
