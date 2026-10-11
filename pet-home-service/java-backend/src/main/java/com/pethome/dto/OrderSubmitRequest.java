package com.pethome.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
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
 * 提交预约订单请求
 *
 * @author pet-home
 */
@Data
public class OrderSubmitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务地址 id */
    @NotNull(message = "地址id不能为空")
    @Min(value = 1, message = "地址id不合法")
    private Long addressId;

    /** 宠物 id */
    @NotNull(message = "宠物id不能为空")
    @Min(value = 1, message = "宠物id不合法")
    private Long petId;

    /** 美容师 id */
    @NotNull(message = "美容师id不能为空")
    @Min(value = 1, message = "美容师id不合法")
    private Long beauticianId;

    /** 预约日期，格式 yyyy-MM-dd */
    @NotNull(message = "预约日期不能为空")
    @FutureOrPresent(message = "预约日期不能早于今天")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;

    /** 预约时段，取值必须为固定时段枚举 */
    @NotBlank(message = "预约时段不能为空")
    @Size(max = 50, message = "预约时段长度不能超过50")
    private String timeSlot;

    /** 服务项列表 */
    @NotEmpty(message = "服务项不能为空")
    @Size(max = 20, message = "单次下单服务项不能超过20个")
    private List<@Valid OrderItemRequest> items;

    /** 备注（选填），写入 service_order.remark */
    @Size(max = 200, message = "备注长度不能超过200")
    private String remark;

}
