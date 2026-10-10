package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单日营业额（Mapper 聚合结果）
 *
 * @author pet-home
 */
@Data
public class StatRevenueItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自然日 */
    private LocalDate statDate;

    /** 当日营业额 */
    private BigDecimal amount;

}
