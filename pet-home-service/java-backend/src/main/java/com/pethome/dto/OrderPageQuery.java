package com.pethome.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户端订单分页查询条件
 *
 * @author pet-home
 */
@Data
public class OrderPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    /** 每页条数 */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer pageSize = 10;

    /** 订单状态筛选 1待支付 2待确认 3待上门 4服务中 5已完成 6已取消 7待评价 */
    @Min(value = 1, message = "订单状态不合法")
    @Max(value = 7, message = "订单状态不合法")
    private Integer status;

}
