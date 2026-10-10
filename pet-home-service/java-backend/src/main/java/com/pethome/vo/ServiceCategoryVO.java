package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端服务分类信息
 *
 * @author pet-home
 */
@Data
public class ServiceCategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    /** 排序值，越小越靠前 */
    private Integer sort;

    /** 状态 1启用 0禁用 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
