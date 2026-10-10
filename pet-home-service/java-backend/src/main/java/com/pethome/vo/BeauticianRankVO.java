package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 美容师业绩排行项
 *
 * @author pet-home
 */
@Data
public class BeauticianRankVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long beauticianId;

    /** 美容师姓名，美容师记录已删除时为 null */
    private String name;

    /** 接单量（已完成订单数） */
    private Long orderCount;

    /** 营业额（已完成订单总额） */
    private BigDecimal amount;

    /** 美容师评分 */
    private BigDecimal score;

}
