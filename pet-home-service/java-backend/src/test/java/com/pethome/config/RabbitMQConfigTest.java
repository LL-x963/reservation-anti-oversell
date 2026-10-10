package com.pethome.config;

import com.pethome.common.constant.MqConstant;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.DirectExchange;
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
    private CustomExchange orderDelayExchange;

    @Autowired
    private DirectExchange orderDirectExchange;

    @Autowired
    private Queue orderCancelQueue;

    @Autowired
    private Queue orderRemindQueue;

    @Autowired
    private Queue orderReminderQueue;

    @Autowired
    private Binding orderCancelBinding;

    @Autowired
    private Binding orderRemindBinding;

    @Autowired
    private Binding orderReminderBinding;

    @Test
    void constantsShouldBeDefined() {
        assertThat(MqConstant.ORDER_DELAY_EXCHANGE).isEqualTo("order.delay");
        assertThat(MqConstant.ORDER_DIRECT_EXCHANGE).isEqualTo("order.direct");
        assertThat(MqConstant.ORDER_CANCEL_QUEUE).isEqualTo("order.cancel.queue");
        assertThat(MqConstant.ORDER_REMIND_QUEUE).isEqualTo("order.remind.queue");
        assertThat(MqConstant.ORDER_REMINDER_QUEUE).isEqualTo("order.reminder.queue");
        assertThat(MqConstant.ORDER_CANCEL_ROUTING_KEY).isEqualTo("order.cancel");
        assertThat(MqConstant.ORDER_REMIND_ROUTING_KEY).isEqualTo("order.remind");
        assertThat(MqConstant.ORDER_REMINDER_ROUTING_KEY).isEqualTo("order.reminder");
        assertThat(MqConstant.DELAYED_EXCHANGE_TYPE).isEqualTo("x-delayed-message");
        assertThat(MqConstant.DELAYED_ARG_NAME).isEqualTo("x-delayed-type");
        assertThat(MqConstant.DELAY_HEADER).isEqualTo("x-delay");
        assertThat(MqConstant.ORDER_CANCEL_DELAY_MILLIS).isEqualTo(30 * 60 * 1000L);
        assertThat(MqConstant.ORDER_REMIND_AHEAD_MILLIS).isEqualTo(60 * 60 * 1000L);
    }

    @Test
    void delayedExchangeBeanShouldBeDelayedType() {
        assertThat(orderDelayExchange.getName()).isEqualTo(MqConstant.ORDER_DELAY_EXCHANGE);
        assertThat(orderDelayExchange.getType()).isEqualTo(MqConstant.DELAYED_EXCHANGE_TYPE);
        assertThat(orderDelayExchange.getArguments())
                .containsEntry(MqConstant.DELAYED_ARG_NAME, MqConstant.DELAYED_ARG_VALUE);
        assertThat(orderDelayExchange.isDurable()).isTrue();
    }

    @Test
    void directExchangeShouldBeDirectType() {
        assertThat(orderDirectExchange.getName()).isEqualTo(MqConstant.ORDER_DIRECT_EXCHANGE);
        assertThat(orderDirectExchange.getType()).isEqualTo("direct");
        assertThat(orderDirectExchange.isDurable()).isTrue();
    }

    @Test
    void queuesShouldBeDurable() {
        assertThat(orderCancelQueue.getName()).isEqualTo(MqConstant.ORDER_CANCEL_QUEUE);
        assertThat(orderRemindQueue.getName()).isEqualTo(MqConstant.ORDER_REMIND_QUEUE);
        assertThat(orderReminderQueue.getName()).isEqualTo(MqConstant.ORDER_REMINDER_QUEUE);
        assertThat(orderCancelQueue.isDurable()).isTrue();
        assertThat(orderRemindQueue.isDurable()).isTrue();
        assertThat(orderReminderQueue.isDurable()).isTrue();
    }

    @Test
    void bindingsShouldBeConfigured() {
        assertThat(orderCancelBinding.getExchange()).isEqualTo(MqConstant.ORDER_DELAY_EXCHANGE);
        assertThat(orderCancelBinding.getRoutingKey()).isEqualTo(MqConstant.ORDER_CANCEL_ROUTING_KEY);
        assertThat(orderCancelBinding.getDestination()).isEqualTo(MqConstant.ORDER_CANCEL_QUEUE);

        assertThat(orderRemindBinding.getExchange()).isEqualTo(MqConstant.ORDER_DELAY_EXCHANGE);
        assertThat(orderRemindBinding.getRoutingKey()).isEqualTo(MqConstant.ORDER_REMIND_ROUTING_KEY);
        assertThat(orderRemindBinding.getDestination()).isEqualTo(MqConstant.ORDER_REMIND_QUEUE);

        assertThat(orderReminderBinding.getExchange()).isEqualTo(MqConstant.ORDER_DIRECT_EXCHANGE);
        assertThat(orderReminderBinding.getRoutingKey()).isEqualTo(MqConstant.ORDER_REMINDER_ROUTING_KEY);
        assertThat(orderReminderBinding.getDestination()).isEqualTo(MqConstant.ORDER_REMINDER_QUEUE);
    }

    @Test
    void rabbitTemplateShouldUseJsonMessageConverter() {
        assertThat(rabbitTemplate.getMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    /**
     * 真实向 Broker 声明交换机、队列与绑定，验证延迟消息插件是否可用
     */
    @Test
    void allExchangesAndQueuesShouldBeDeclarableOnBroker() {
        assertThatCode(() -> {
            rabbitAdmin.declareExchange(orderDelayExchange);
            rabbitAdmin.declareExchange(orderDirectExchange);
            rabbitAdmin.declareQueue(orderCancelQueue);
            rabbitAdmin.declareQueue(orderRemindQueue);
            rabbitAdmin.declareQueue(orderReminderQueue);
            rabbitAdmin.declareBinding(orderCancelBinding);
            rabbitAdmin.declareBinding(orderRemindBinding);
            rabbitAdmin.declareBinding(orderReminderBinding);
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
