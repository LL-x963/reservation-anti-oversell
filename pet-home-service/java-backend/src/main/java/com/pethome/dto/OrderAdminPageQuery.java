package com.pethome.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 管理端订单分页查询条件
 *
 * @author pet-home
 */
@Data
public class OrderAdminPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    /** 每页条数 */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer pageSize = 10;

    /** 订单状态筛选 */
    @Min(value = 1, message = "订单状态不合法")
    @Max(value = 7, message = "订单状态不合法")
    private Integer status;

    /** 预约日期筛选，格式 yyyy-MM-dd */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;

    /** 美容师筛选 */
    @Min(value = 1, message = "美容师id不合法")
    private Long beauticianId;

}
