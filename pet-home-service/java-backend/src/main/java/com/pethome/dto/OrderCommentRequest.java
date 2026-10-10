package com.pethome.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 订单评价请求
 *
 * @author pet-home
 */
@Data
public class OrderCommentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单 id */
    @NotNull(message = "订单id不能为空")
    @Min(value = 1, message = "订单id不合法")
    private Long orderId;

    /** 评分 1-5 */
    @NotNull(message = "评分不能为空")
    @Min(value = 1, message = "评分不能低于1分")
    @Max(value = 5, message = "评分不能高于5分")
    private Integer score;

    /** 评价内容 */
    @Size(max = 500, message = "评价内容长度不能超过500")
    private String content;

}
