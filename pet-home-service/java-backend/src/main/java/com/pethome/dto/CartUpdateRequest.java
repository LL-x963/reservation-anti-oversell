package com.pethome.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改预约车服务数量请求
 *
 * @author pet-home
 */
@Data
public class CartUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务项目 id */
    @NotNull(message = "服务id不能为空")
    @Min(value = 1, message = "服务id不合法")
    private Long serviceId;

    /** 数量，直接覆盖原有数量 */
    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量不能小于1")
    @Max(value = 99, message = "数量不能超过99")
    private Integer quantity;

}
