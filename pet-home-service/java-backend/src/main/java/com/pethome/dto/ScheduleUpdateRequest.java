package com.pethome.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改排班可用状态请求
 *
 * @author pet-home
 */
@Data
public class ScheduleUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否可接单 1可接单 0不可接单 */
    @NotNull(message = "可用状态不能为空")
    @Min(value = 0, message = "可用状态只能为1（可接单）或0（不可接单）")
    @Max(value = 1, message = "可用状态只能为1（可接单）或0（不可接单）")
    private Integer available;

}
