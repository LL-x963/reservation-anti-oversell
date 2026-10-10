package com.pethome.config;

import com.pethome.common.constant.MqConstant;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * RabbitMQ 配置测试（需本地 RabbitMQ 且已安装延迟消息插件）
 *
 * @author pet-home
 */
@SpringBootTest
class RabbitMQConfigTest {

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CustomExchange delayedExchange;

    @Autowired
    private Queue orderTimeoutQueue;

    @Autowired
    private Binding orderTimeoutBinding;

    @Test
    void constantsShouldBeDefined() {
        assertThat(MqConstant.DELAYED_EXCHANGE).isEqualTo("pethome.delayed.exchange");
        assertThat(MqConstant.ORDER_TIMEOUT_QUEUE).isEqualTo("pethome.order.timeout.queue");
        assertThat(MqConstant.ORDER_TIMEOUT_ROUTING_KEY).isEqualTo("pethome.order.timeout");
        assertThat(MqConstant.DELAYED_EXCHANGE_TYPE).isEqualTo("x-delayed-message");
        assertThat(MqConstant.DELAYED_ARG_NAME).isEqualTo("x-delayed-type");
        assertThat(MqConstant.DELAY_HEADER).isEqualTo("x-delay");
    }

    @Test
    void delayedExchangeBeanShouldBeDelayedType() {
        assertThat(delayedExchange.getName()).isEqualTo(MqConstant.DELAYED_EXCHANGE);
        assertThat(delayedExchange.getType()).isEqualTo(MqConstant.DELAYED_EXCHANGE_TYPE);
        assertThat(delayedExchange.getArguments()).containsEntry(MqConstant.DELAYED_ARG_NAME, MqConstant.DELAYED_ARG_VALUE);
        assertThat(delayedExchange.isDurable()).isTrue();
    }

    @Test
    void queueAndBindingShouldBeConfigured() {
        assertThat(orderTimeoutQueue.getName()).isEqualTo(MqConstant.ORDER_TIMEOUT_QUEUE);
        assertThat(orderTimeoutQueue.isDurable()).isTrue();

        assertThat(orderTimeoutBinding.getExchange()).isEqualTo(MqConstant.DELAYED_EXCHANGE);
        assertThat(orderTimeoutBinding.getRoutingKey()).isEqualTo(MqConstant.ORDER_TIMEOUT_ROUTING_KEY);
        assertThat(orderTimeoutBinding.getDestination()).isEqualTo(MqConstant.ORDER_TIMEOUT_QUEUE);
    }

    @Test
    void rabbitTemplateShouldUseJsonMessageConverter() {
        assertThat(rabbitTemplate.getMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    /**
     * 真实向 Broker 声明延迟交换机、队列与绑定，验证延迟消息插件是否可用
     */
    @Test
    void delayedExchangeShouldBeDeclarableOnBroker() {
        assertThatCode(() -> {
            rabbitAdmin.declareExchange(delayedExchange);
            rabbitAdmin.declareQueue(orderTimeoutQueue);
            rabbitAdmin.declareBinding(orderTimeoutBinding);
        }).doesNotThrowAnyException();
    }

    @Test
    void messageConverterShouldSerializeObjectAsJson() {
        Message message = rabbitTemplate.getMessageConverter()
                .toMessage(Map.of("orderId", 1001L, "reason", "超时未支付"), new MessageProperties());

        assertThat(new String(message.getBody(), StandardCharsets.UTF_8))
                .contains("orderId")
                .contains("超时未支付");
    }

}
