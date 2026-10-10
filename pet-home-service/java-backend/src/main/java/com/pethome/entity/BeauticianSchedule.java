package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 美容师排班表
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("beautician_schedule")
public class BeauticianSchedule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 美容师 id */
    private Long beauticianId;

    /** 排班日期 */
    @TableField("date")
    private LocalDate date;

    /** 时段，取值见 TimeSlot */
    private String timeSlot;

    /** 是否可接单 1可接单 0已占用 */
    private Integer available;

    /** 占用订单 id，未占用为 null */
    private Long orderId;

}
