package com.pethome.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用户复购统计结果
 *
 * @author pet-home
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RepurchaseVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 复购用户数（已完成订单 ≥ 2 笔的用户） */
    private Long repeatUsers;

    /** 总用户数（有已完成订单的用户） */
    private Long totalUsers;

    /** 复购率（0~1，保留 4 位小数） */
    private BigDecimal repurchaseRate;

}
