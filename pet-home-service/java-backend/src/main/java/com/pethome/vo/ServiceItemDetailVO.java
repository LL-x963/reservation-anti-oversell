package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用户端服务项目详情
 *
 * @author pet-home
 */
@Data
public class ServiceItemDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long categoryId;

    private String name;

    private BigDecimal price;

    /** 服务时长（分钟） */
    private Integer serviceDuration;

    /** 适配宠物类型 猫/狗/通用 */
    private String petType;

    private String suitWeight;

    private String description;

    private String image;

}
