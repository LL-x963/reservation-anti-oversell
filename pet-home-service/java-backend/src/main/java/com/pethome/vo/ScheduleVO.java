package com.pethome.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排班信息
 *
 * @author pet-home
 */
@Data
public class ScheduleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long beauticianId;

    /** 排班日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** 时段 */
    private String timeSlot;

    /** 是否可接单 1可接单 0已占用 */
    private Integer available;

    /** 占用订单 id */
    private Long orderId;

    /** 是否已被订单占用（available=0 且 orderId 不为空） */
    private Boolean occupied;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
