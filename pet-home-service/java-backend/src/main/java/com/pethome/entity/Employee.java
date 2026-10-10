package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员（员工）表
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("employee")
public class Employee extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    private String username;

    /** 姓名 */
    private String name;

    /** 密码（BCrypt 密文） */
    private String password;

    /** 手机号 */
    private String phone;

    /** 性别 0未知 1男 2女 */
    private Integer sex;

    /** 身份证号 */
    private String idNumber;

    /** 头像 */
    private String avatar;

    /** 状态 1启用 0禁用 */
    private Integer status;

}
