package com.pethome.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 营业额统计结果
 *
 * @author pet-home
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatRevenueVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日期列表（区间内逐日，无数据的日期补 0） */
    private List<LocalDate> dates;

    /** 与日期列表一一对应的营业额列表 */
    private List<BigDecimal> revenues;

    /** 区间总营业额 */
    private BigDecimal totalAmount;

}
