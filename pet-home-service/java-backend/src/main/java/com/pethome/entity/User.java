package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户表
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 微信 openid */
    private String openid;

    /** 姓名 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 性别 0未知 1男 2女 */
    private Integer sex;

    /** 身份证号 */
    private String idNumber;

    /** 头像 */
    private String avatar;

}
