package com.pethome.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 批量设置排班请求
 *
 * @author pet-home
 */
@Data
public class ScheduleBatchSetRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 美容师 id */
    @NotNull(message = "美容师id不能为空")
    @Min(value = 1, message = "美容师id不合法")
    private Long beauticianId;

    /** 排班日期，格式 yyyy-MM-dd */
    @NotNull(message = "排班日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** 时段列表，取值必须为固定时段枚举 */
    @NotEmpty(message = "时段不能为空")
    @Size(max = 5, message = "时段数量不能超过5个")
    private List<@NotBlank(message = "时段不能为空") String> timeSlots;

}
