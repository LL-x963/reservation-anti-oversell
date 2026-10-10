package com.pethome.common.constant;

/**
 * 业务错误码常量（通过 Result.bizCode 透出给前端）
 *
 * @author pet-home
 */
public final class ErrorCodeConstant {

    private ErrorCodeConstant() {
    }

    /** 该时段不可约 */
    public static final int SLOT_NOT_AVAILABLE = 601;

    /** 该时段已被预约 */
    public static final int SLOT_ALREADY_BOOKED = 602;

    /** 催单过于频繁，请稍后再试 */
    public static final int REMINDER_TOO_FREQUENT = 603;

}
