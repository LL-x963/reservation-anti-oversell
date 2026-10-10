package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单评价信息
 *
 * @author pet-home
 */
@Data
public class OrderCommentVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 评分 1-5 */
    private Integer score;

    /** 评价内容 */
    private String content;

    /** 评价时间 */
    private LocalDateTime createTime;

}
