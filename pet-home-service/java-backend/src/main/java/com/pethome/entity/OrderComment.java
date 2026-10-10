package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单评价表
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_comment")
public class OrderComment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 id，一个订单仅允许评价一次 */
    private Long orderId;

    /** 评价用户 id */
    private Long userId;

    /** 被评价的美容师 id */
    private Long beauticianId;

    /** 评分 1-5 */
    private Integer score;

    /** 评价内容 */
    private String content;

}
