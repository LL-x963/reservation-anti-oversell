package com.pethome.common.enums;

import lombok.Getter;

/**
 * 订单状态枚举
 *
 * <p>状态流转：1待支付 →(支付)→ 2待确认 →(确认)→ 3待上门 →(开始服务)→ 4服务中
 * →(完成)→ 7待评价 →(评价)→ 5已完成；任意非终态可 → 6已取消。</p>
 *
 * @author pet-home
 */
@Getter
public enum OrderStatus {

    /** 待支付 */
    PENDING_PAY(1, "待支付"),
    /** 待确认 */
    PENDING_CONFIRM(2, "待确认"),
    /** 待上门 */
    PENDING_VISIT(3, "待上门"),
    /** 服务中 */
    IN_SERVICE(4, "服务中"),
    /** 已完成 */
    COMPLETED(5, "已完成"),
    /** 已取消 */
    CANCELED(6, "已取消"),
    /** 待评价 */
    PENDING_COMMENT(7, "待评价");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static OrderStatus fromCode(Integer code) {
        if (code != null) {
            for (OrderStatus status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("未知的订单状态: " + code);
    }

    /**
     * 是否为未完成状态（1待支付 ~ 4服务中），用于关联数据删除校验
     */
    public static boolean isUnfinished(Integer code) {
        return code != null && code >= PENDING_PAY.code && code <= IN_SERVICE.code;
    }

    /**
     * 状态描述，未知状态返回 null
     */
    public static String descOf(Integer code) {
        if (code != null) {
            for (OrderStatus status : values()) {
                if (status.code == code) {
                    return status.desc;
                }
            }
        }
        return null;
    }

    public boolean is(Integer code) {
        return code != null && code == this.code;
    }

}
