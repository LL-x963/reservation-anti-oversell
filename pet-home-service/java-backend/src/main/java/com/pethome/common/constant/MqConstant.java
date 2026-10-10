package com.pethome.common.constant;

/**
 * RabbitMQ 常量：交换机、队列、路由键
 *
 * @author pet-home
 */
public final class MqConstant {

    private MqConstant() {
    }

    // ==================== 延迟交换机 ====================

    /** 延迟交换机名称（需 rabbitmq_delayed_message_exchange 插件支持） */
    public static final String DELAYED_EXCHANGE = "pethome.delayed.exchange";

    /** 延迟交换机类型 */
    public static final String DELAYED_EXCHANGE_TYPE = "x-delayed-message";

    /** 延迟交换机内部实际转发类型参数名 */
    public static final String DELAYED_ARG_NAME = "x-delayed-type";

    /** 延迟交换机内部实际转发类型 */
    public static final String DELAYED_ARG_VALUE = "direct";

    /** 消息延迟时间 header（单位：毫秒） */
    public static final String DELAY_HEADER = "x-delay";

    // ==================== 订单超时队列 ====================

    /** 订单超时未支付队列 */
    public static final String ORDER_TIMEOUT_QUEUE = "pethome.order.timeout.queue";

    /** 订单超时未支付路由键 */
    public static final String ORDER_TIMEOUT_ROUTING_KEY = "pethome.order.timeout";

    // ==================== 常用延迟时长（毫秒） ====================

    /** 15 分钟 */
    public static final long DELAY_15_MINUTES = 15 * 60 * 1000L;

    /** 30 分钟 */
    public static final long DELAY_30_MINUTES = 30 * 60 * 1000L;

    /** 24 小时 */
    public static final long DELAY_24_HOURS = 24 * 60 * 60 * 1000L;

}
