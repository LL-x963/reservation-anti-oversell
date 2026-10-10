package com.pethome.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 修改服务项目请求（全量更新，上下架请使用专门的上下架接口）
 *
 * @author pet-home
 */
@Data
public class ServiceItemUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务项目 id */
    @NotNull(message = "服务项目id不能为空")
    @Min(value = 1, message = "服务项目id不合法")
    private Long id;

    /** 所属分类 id */
    @NotNull(message = "分类id不能为空")
    @Min(value = 1, message = "分类id不合法")
    private Long categoryId;

    /** 服务名称 */
    @NotBlank(message = "服务名称不能为空")
    @Size(max = 100, message = "服务名称长度不能超过100")
    private String name;

    /** 价格 */
    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    @Digits(integer = 8, fraction = 2, message = "价格整数位不能超过8位，小数位不能超过2位")
    private BigDecimal price;

    /** 服务时长（分钟） */
    @Min(value = 1, message = "服务时长不能小于1分钟")
    @Max(value = 1440, message = "服务时长不能超过1440分钟")
    private Integer serviceDuration;

    /** 适配宠物类型 猫/狗/通用 */
    @Size(max = 20, message = "适配宠物类型长度不能超过20")
    private String petType;

    /** 适配体重 */
    @Size(max = 50, message = "适配体重长度不能超过50")
    private String suitWeight;

    /** 描述 */
    @Size(max = 1000, message = "描述长度不能超过1000")
    private String description;

    /** 图片地址 */
    @Size(max = 255, message = "图片地址长度不能超过255")
    @Pattern(regexp = "^(https?://|/).+", message = "图片地址必须以 http(s):// 或 / 开头")
    private String image;

}
