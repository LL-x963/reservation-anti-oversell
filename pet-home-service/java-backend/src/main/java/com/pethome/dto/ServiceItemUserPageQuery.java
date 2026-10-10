package com.pethome.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用户端服务项目分页查询条件（仅查询上架服务）
 *
 * @author pet-home
 */
@Data
public class ServiceItemUserPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    /** 每页条数 */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer pageSize = 10;

    /** 分类 id 筛选 */
    @Min(value = 1, message = "分类id不合法")
    private Long categoryId;

    /** 适配宠物类型筛选 */
    @Size(max = 20, message = "适配宠物类型长度不能超过20")
    private String petType;

    /** 价格区间下限（含） */
    @DecimalMin(value = "0", message = "最低价格不能小于0")
    private BigDecimal minPrice;

    /** 价格区间上限（含） */
    @DecimalMin(value = "0", message = "最高价格不能小于0")
    private BigDecimal maxPrice;

}
