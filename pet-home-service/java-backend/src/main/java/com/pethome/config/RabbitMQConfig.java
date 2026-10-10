package com.pethome.config;

import com.pethome.common.constant.MqConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 基础配置
 *
 * <p>1. 连接工厂由 spring-boot-starter-amqp 依据 application.yaml 中 spring.rabbitmq.* 自动装配；
 * 这里基于连接工厂定制 {@link RabbitTemplate}（JSON 消息体 + 发送确认 + 路由失败回调）。<br>
 * 2. 延迟消息使用 RabbitMQ 官方延迟消息插件，交换机类型为 {@code x-delayed-message}，
 * 需要服务端已安装 rabbitmq_delayed_message_exchange 插件。<br>
 * 3. 若插件不可用，将 {@code pethome.mq.delayed-enabled} 置为 false 即可正常启动（延迟相关 Bean 不注册）。</p>
 *
 * @author pet-home
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    /**
     * 消息体统一使用 JSON 序列化
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 定制 RabbitTemplate：JSON 消息体、开启 mandatory、注册发送确认与路由失败回调
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jackson2JsonMessageConverter);
        // 消息无法路由到队列时回调 ReturnsCallback
        rabbitTemplate.setMandatory(true);
        // 消息是否成功到达交换机
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("消息发送到交换机失败, correlationDataId={}, cause={}",
                        correlationData == null ? null : correlationData.getId(), cause);
            }
        });
        // 消息从交换机路由到队列失败
        rabbitTemplate.setReturnsCallback(returned -> log.error("消息路由到队列失败, exchange={}, routingKey={}, reply={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getReplyText()));
        return rabbitTemplate;
    }

    /**
     * 延迟消息相关声明，依赖 rabbitmq_delayed_message_exchange 插件
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "pethome.mq", name = "delayed-enabled",
            havingValue = "true", matchIfMissing = true)
    public static class DelayedExchangeConfiguration {

        /**
         * 延迟交换机：x-delayed-message，内部按 direct 路由
         */
        @Bean
        public CustomExchange delayedExchange() {
            Map<String, Object> arguments = new HashMap<>(2);
            arguments.put(MqConstant.DELAYED_ARG_NAME, MqConstant.DELAYED_ARG_VALUE);
            return new CustomExchange(MqConstant.DELAYED_EXCHANGE, MqConstant.DELAYED_EXCHANGE_TYPE,
                    true, false, arguments);
        }

        /**
         * 订单超时未支付队列
         */
        @Bean
        public Queue orderTimeoutQueue() {
            return QueueBuilder.durable(MqConstant.ORDER_TIMEOUT_QUEUE).build();
        }

        /**
         * 队列与延迟交换机绑定
         */
        @Bean
        public Binding orderTimeoutBinding(Queue orderTimeoutQueue, CustomExchange delayedExchange) {
            return BindingBuilder.bind(orderTimeoutQueue)
                    .to(delayedExchange)
                    .with(MqConstant.ORDER_TIMEOUT_ROUTING_KEY)
                    .noargs();
        }
    }

}
