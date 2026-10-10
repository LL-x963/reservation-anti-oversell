package com.pethome.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 添加服务到预约车请求
 *
 * @author pet-home
 */
@Data
public class CartAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务项目 id */
    @NotNull(message = "服务id不能为空")
    @Min(value = 1, message = "服务id不合法")
    private Long serviceId;

    /** 数量，已存在该服务时在此基础上累加 */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量不能小于1")
    @Max(value = 99, message = "数量不能超过99")
    private Integer quantity;

}
