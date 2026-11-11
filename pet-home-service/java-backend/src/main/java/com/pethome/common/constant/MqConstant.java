package com.pethome.common.constant;

/**
 * RabbitMQ 常量：交换机、队列、路由键
 *
 * @author pet-home
 */
public final class MqConstant {

    private MqConstant() {
    }

    // ==================== 交换机 ====================

    /** 订单延迟交换机（需 rabbitmq_delayed_message_exchange 插件支持） */
    public static final String ORDER_DELAY_EXCHANGE = "order.delay";

    /** 订单普通交换机 */
    public static final String ORDER_DIRECT_EXCHANGE = "order.direct";

    /** 延迟交换机类型 */
    public static final String DELAYED_EXCHANGE_TYPE = "x-delayed-message";

    /** 延迟交换机内部实际转发类型参数名 */
    public static final String DELAYED_ARG_NAME = "x-delayed-type";

    /** 延迟交换机内部实际转发类型 */
    public static final String DELAYED_ARG_VALUE = "direct";

    /** 消息延迟时间 header（单位：毫秒） */
    public static final String DELAY_HEADER = "x-delay";

    /** 死信交换机：消费重试耗尽后的消息转发到这里，避免静默丢失 */
    public static final String ORDER_DLX_EXCHANGE = "order.dlx";

    /** 死信队列 */
    public static final String ORDER_DLQ_QUEUE = "order.dlq.queue";

    /** 死信路由键 */
    public static final String ORDER_DLQ_ROUTING_KEY = "order.dlq";

    // ==================== 队列 ====================

    /** 订单超时未支付取消队列 */
    public static final String ORDER_CANCEL_QUEUE = "order.cancel.queue";

    /** 预约到点提醒队列 */
    public static final String ORDER_REMIND_QUEUE = "order.remind.queue";

    /** 催单即时通知队列 */
    public static final String ORDER_REMINDER_QUEUE = "order.reminder.queue";

    // ==================== 路由键 ====================

    /** 超时取消路由键 */
    public static final String ORDER_CANCEL_ROUTING_KEY = "order.cancel";

    /** 到点提醒路由键 */
    public static final String ORDER_REMIND_ROUTING_KEY = "order.remind";

    /** 催单通知路由键 */
    public static final String ORDER_REMINDER_ROUTING_KEY = "order.reminder";

    // ==================== 业务常量 ====================

    /** 待支付超时取消延迟：30 分钟 */
    public static final long ORDER_CANCEL_DELAY_MILLIS = 30 * 60 * 1000L;

    /** 预约开始前提醒提前量：1 小时 */
    public static final long ORDER_REMIND_AHEAD_MILLIS = 60 * 60 * 1000L;

    /** 超时取消消息 id 前缀，完整形如 cancel:100 */
    public static final String CANCEL_MESSAGE_ID_PREFIX = "cancel:";

    /** 到点提醒消息 id 前缀，完整形如 remind:100 */
    public static final String REMIND_MESSAGE_ID_PREFIX = "remind:";

}
