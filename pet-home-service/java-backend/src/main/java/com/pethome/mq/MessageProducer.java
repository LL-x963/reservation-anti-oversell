package com.pethome.mq;

import com.pethome.common.constant.MqConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 消息发送封装
 *
 * @author pet-home
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送延迟消息
     *
     * @param routingKey  路由键，例如 {@link MqConstant#ORDER_TIMEOUT_ROUTING_KEY}
     * @param payload     消息体
     * @param delayMillis 延迟毫秒数，通过 x-delay 头实现
     */
    public void sendDelayed(String routingKey, Object payload, long delayMillis) {
        rabbitTemplate.convertAndSend(MqConstant.DELAYED_EXCHANGE, routingKey, payload, message -> {
            message.getMessageProperties().setHeader(MqConstant.DELAY_HEADER, delayMillis);
            return message;
        });
        log.info("发送延迟消息: routingKey={}, delayMillis={}, payload={}", routingKey, delayMillis, payload);
    }

    /**
     * 发送普通消息
     */
    public void send(String exchange, String routingKey, Object payload) {
        rabbitTemplate.convertAndSend(exchange, routingKey, payload);
        log.info("发送消息: exchange={}, routingKey={}, payload={}", exchange, routingKey, payload);
    }

}
