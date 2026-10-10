package com.pethome.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 批量设置排班时被跳过的时段
 *
 * @author pet-home
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleSkippedVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 被跳过的时段 */
    private String timeSlot;

    /** 占用该时段的订单 id */
    private Long orderId;

    /** 跳过原因 */
    private String reason;

}
