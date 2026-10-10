package com.pethome.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 营业额统计查询条件
 *
 * @author pet-home
 */
@Data
public class StatRevenueQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 基准日期，格式 yyyy-MM-dd，不传默认今天 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** 统计粒度：day 当天、week 所在自然周、month 所在自然月，不传默认 day */
    @Pattern(regexp = "day|week|month", message = "统计粒度只能为 day、week 或 month")
    private String type = "day";

}
