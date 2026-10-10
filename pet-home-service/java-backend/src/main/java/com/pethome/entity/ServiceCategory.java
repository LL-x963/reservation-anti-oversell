package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 服务分类表
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_category")
public class ServiceCategory extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 分类名称，全局唯一（uk_name） */
    private String name;

    /** 排序值，越小越靠前 */
    private Integer sort;

    /** 状态 1启用 0禁用 */
    private Integer status;

}
