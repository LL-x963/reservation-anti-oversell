package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户端服务分类信息（仅返回必要字段）
 *
 * @author pet-home
 */
@Data
public class ServiceCategorySimpleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    /** 排序值，越小越靠前 */
    private Integer sort;

}
