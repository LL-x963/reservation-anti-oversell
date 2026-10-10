package com.pethome.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 服务项目上下架请求
 *
 * @author pet-home
 */
@Data
public class ServiceItemStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态 1上架 0下架 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为1（上架）或0（下架）")
    @Max(value = 1, message = "状态只能为1（上架）或0（下架）")
    private Integer status;

}
