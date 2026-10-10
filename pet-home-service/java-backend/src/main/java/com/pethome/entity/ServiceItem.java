package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 服务项目表
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_item")
public class ServiceItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属分类 id */
    private Long categoryId;

    /** 服务名称，同一分类内唯一（uk_category_name） */
    private String name;

    /** 价格 */
    private BigDecimal price;

    /** 服务时长（分钟） */
    private Integer serviceDuration;

    /** 适配宠物类型 猫/狗/通用 */
    private String petType;

    /** 适配体重 */
    private String suitWeight;

    /** 描述 */
    private String description;

    /** 图片地址 */
    private String image;

    /** 状态 1上架 0下架 */
    private Integer status;

}
