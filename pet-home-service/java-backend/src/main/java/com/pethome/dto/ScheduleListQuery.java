package com.pethome.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 排班列表查询条件
 *
 * @author pet-home
 */
@Data
public class ScheduleListQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 美容师 id */
    @NotNull(message = "美容师id不能为空")
    @Min(value = 1, message = "美容师id不合法")
    private Long beauticianId;

    /** 排班日期，格式 yyyy-MM-dd */
    @NotNull(message = "排班日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

}
