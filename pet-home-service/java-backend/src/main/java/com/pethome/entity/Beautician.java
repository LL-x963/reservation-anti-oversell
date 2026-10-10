package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 美容师表
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("beautician")
public class Beautician extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 姓名 */
    private String name;

    /** 头像 */
    private String avatar;

    /** 技能标签，数据库中以英文逗号分隔存储 */
    private String skillTags;

    /** 服务范围 */
    private String serviceRange;

    /** 入职时间 */
    private LocalDate entryTime;

    /** 评分，由评价体系维护 */
    private BigDecimal score;

    /** 状态 1在职 0离职 */
    private Integer status;

}
