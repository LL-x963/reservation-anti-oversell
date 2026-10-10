package com.pethome.mq;

import com.pethome.common.constant.MqConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * MQ 消息发送工具类
 *
 * <p>统一封装延迟消息（x-delay 头）与普通消息的发送，业务层不再直接操作 RabbitTemplate。</p>
 *
 * @author pet-home
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqMessageSender {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送订单超时取消延迟消息（30 分钟）
     *
     * @param orderId     订单 id
     * @param delayMillis 延迟毫秒数
     */
    public void sendOrderCancelDelayed(Long orderId, long delayMillis) {
        sendDelayed(MqConstant.ORDER_CANCEL_ROUTING_KEY, orderId, delayMillis,
                MqConstant.CANCEL_MESSAGE_ID_PREFIX + orderId);
    }

    /**
     * 发送预约到点提醒延迟消息
     *
     * @param orderId     订单 id
     * @param delayMillis 延迟毫秒数
     */
    public void sendOrderRemindDelayed(Long orderId, long delayMillis) {
        sendDelayed(MqConstant.ORDER_REMIND_ROUTING_KEY, orderId, delayMillis,
                MqConstant.REMIND_MESSAGE_ID_PREFIX + orderId);
    }

    /**
     * 发送催单即时通知
     */
    public void sendOrderReminderNotify(Long orderId) {
        send(MqConstant.ORDER_DIRECT_EXCHANGE, MqConstant.ORDER_REMINDER_ROUTING_KEY, orderId);
    }

    /**
     * 发送延迟消息（走订单延迟交换机）
     *
     * @param routingKey 路由键
     * @param payload    消息体
     * @param delayMillis 延迟毫秒数，小于 0 按 0 处理（立即投递）
     */
    public void sendDelayed(String routingKey, Object payload, long delayMillis) {
        sendDelayed(routingKey, payload, delayMillis, null);
    }

    /**
     * 发送延迟消息（走订单延迟交换机），可指定消息 id 用于发送确认追踪
     */
    public void sendDelayed(String routingKey, Object payload, long delayMillis, String correlationId) {
        long delay = Math.max(delayMillis, 0L);
        MessagePostProcessor postProcessor = message -> {
            message.getMessageProperties().setHeader(MqConstant.DELAY_HEADER, delay);
            return message;
        };
        CorrelationData correlationData = correlationId == null ? null : new CorrelationData(correlationId);

        rabbitTemplate.convertAndSend(MqConstant.ORDER_DELAY_EXCHANGE, routingKey, payload, postProcessor, correlationData);
        log.info("发送延迟消息: exchange={}, routingKey={}, payload={}, delayMillis={}, messageId={}",
                MqConstant.ORDER_DELAY_EXCHANGE, routingKey, payload, delay, correlationId);
    }

    /**
     * 发送普通消息
     */
    public void send(String exchange, String routingKey, Object payload) {
        rabbitTemplate.convertAndSend(exchange, routingKey, payload);
        log.info("发送消息: exchange={}, routingKey={}, payload={}", exchange, routingKey, payload);
    }

}
