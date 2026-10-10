package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 宠物档案表
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pet_profile")
public class PetProfile extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 归属用户 id，由服务端根据登录态自动绑定 */
    private Long userId;

    /** 宠物名称，同一用户下唯一（uk_user_name） */
    private String name;

    /** 品种 */
    private String breed;

    /** 性别 0未知 1公 2母 */
    private Integer gender;

    /** 生日 */
    private LocalDate birthday;

    /** 体重(kg) */
    private BigDecimal weight;

    /** 疫苗情况 */
    private String vaccine;

    /** 备注 */
    private String remark;

}
