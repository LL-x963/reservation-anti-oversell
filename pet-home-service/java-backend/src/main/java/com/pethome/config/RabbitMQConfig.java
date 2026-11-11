package com.pethome.config;

import com.pethome.common.constant.MqConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 基础配置
 *
 * <p>1. 连接工厂由 spring-boot-starter-amqp 依据 application.yaml 中 spring.rabbitmq.* 自动装配，
 * 这里基于连接工厂定制 {@link RabbitTemplate}（JSON 消息体 + 发送确认 + 路由失败回调）。<br>
 * 2. 延迟消息使用 RabbitMQ 官方延迟消息插件，交换机类型为 {@code x-delayed-message}。<br>
 * 3. 若插件不可用，将 {@code pethome.mq.delayed-enabled} 置为 false 即可正常启动
 * （延迟相关 Bean 不注册，普通交换机与催单队列不受影响）。</p>
 *
 * <p>拓扑：<br>
 * - order.delay（延迟交换机）→ order.cancel.queue（路由键 order.cancel）<br>
 * - order.delay（延迟交换机）→ order.remind.queue（路由键 order.remind）<br>
 * - order.direct（普通交换机）→ order.reminder.queue（路由键 order.reminder）</p>
 *
 * @author pet-home
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    /**
     * 显式声明 RabbitAdmin：用于启动期声明交换机/队列/绑定，避免首条业务消息早于拓扑声明而 NO_ROUTE
     */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        rabbitAdmin.setAutoStartup(true);
        return rabbitAdmin;
    }

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
        // 注意：这里刻意不开 mandatory。
        // 延迟消息插件的 x-delayed-message 交换机在「投递时点」无法立即确定路由目标（消息由插件暂存、
        // 到点后再投递），若开启 mandatory，Broker 会立即回 basic.return(NO_ROUTE)，
        // 造成「延迟消息被误判为路由失败」的假象。故延迟场景下 mandatory 必须关闭。
        // 消息是否成功到达交换机仍由 Publisher Confirm 保障。
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("消息发送到交换机失败, correlationDataId={}, cause={}",
                        correlationData == null ? null : correlationData.getId(), cause);
            }
        });
        return rabbitTemplate;
    }

    /**
     * 订单普通交换机与催单通知队列
     */
    @Configuration(proxyBeanMethods = false)
    public static class DirectExchangeConfiguration {

        /**
         * 订单普通交换机
         */
        @Bean
        public DirectExchange orderDirectExchange() {
            return ExchangeBuilder.directExchange(MqConstant.ORDER_DIRECT_EXCHANGE).durable(true).build();
        }

        /**
         * 催单即时通知队列
         */
        @Bean
        public Queue orderReminderQueue() {
            return QueueBuilder.durable(MqConstant.ORDER_REMINDER_QUEUE).build();
        }

        /**
         * 催单通知队列绑定到普通交换机
         */
        @Bean
        public Binding orderReminderBinding(Queue orderReminderQueue, DirectExchange orderDirectExchange) {
            return BindingBuilder.bind(orderReminderQueue)
                    .to(orderDirectExchange)
                    .with(MqConstant.ORDER_REMINDER_ROUTING_KEY);
        }
    }

    /**
     * 死信拓扑与消费失败兜底。
     *
     * <p>消费端开启重试（见 application.yaml 中 spring.rabbitmq.listener.simple.retry），
     * 重试耗尽后由 {@link RepublishMessageRecoverer} 把消息转发到死信队列，
     * 而不是直接丢弃或无限重回原队列。</p>
     */
    @Configuration(proxyBeanMethods = false)
    public static class DeadLetterConfiguration {

        @Bean
        public DirectExchange orderDlxExchange() {
            return ExchangeBuilder.directExchange(MqConstant.ORDER_DLX_EXCHANGE).durable(true).build();
        }

        @Bean
        public Queue orderDlqQueue() {
            return QueueBuilder.durable(MqConstant.ORDER_DLQ_QUEUE).build();
        }

        @Bean
        public Binding orderDlqBinding(Queue orderDlqQueue, DirectExchange orderDlxExchange) {
            return BindingBuilder.bind(orderDlqQueue).to(orderDlxExchange).with(MqConstant.ORDER_DLQ_ROUTING_KEY);
        }

        /**
         * 重试耗尽后的消息兜底：转发到死信队列，保留原始消息与异常头信息
         */
        @Bean
        public MessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate) {
            return new RepublishMessageRecoverer(rabbitTemplate,
                    MqConstant.ORDER_DLX_EXCHANGE, MqConstant.ORDER_DLQ_ROUTING_KEY);
        }
    }

    /**
     * 订单延迟交换机与超时取消、到点提醒队列，依赖 rabbitmq_delayed_message_exchange 插件
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "pethome.mq", name = "delayed-enabled",
            havingValue = "true", matchIfMissing = true)
    public static class DelayedExchangeConfiguration {

        /**
         * 订单延迟交换机：x-delayed-message，内部按 direct 路由
         */
        @Bean
        public CustomExchange orderDelayExchange() {
            Map<String, Object> arguments = new HashMap<>(2);
            arguments.put(MqConstant.DELAYED_ARG_NAME, MqConstant.DELAYED_ARG_VALUE);
            return new CustomExchange(MqConstant.ORDER_DELAY_EXCHANGE, MqConstant.DELAYED_EXCHANGE_TYPE,
                    true, false, arguments);
        }

        /**
         * 订单超时未支付取消队列
         */
        @Bean
        public Queue orderCancelQueue() {
            return QueueBuilder.durable(MqConstant.ORDER_CANCEL_QUEUE).build();
        }

        /**
         * 预约到点提醒队列
         */
        @Bean
        public Queue orderRemindQueue() {
            return QueueBuilder.durable(MqConstant.ORDER_REMIND_QUEUE).build();
        }

        /**
         * 超时取消队列绑定到延迟交换机
         */
        @Bean
        public Binding orderCancelBinding(Queue orderCancelQueue, CustomExchange orderDelayExchange) {
            return BindingBuilder.bind(orderCancelQueue)
                    .to(orderDelayExchange)
                    .with(MqConstant.ORDER_CANCEL_ROUTING_KEY)
                    .noargs();
        }

        /**
         * 到点提醒队列绑定到延迟交换机
         */
        @Bean
        public Binding orderRemindBinding(Queue orderRemindQueue, CustomExchange orderDelayExchange) {
            return BindingBuilder.bind(orderRemindQueue)
                    .to(orderDelayExchange)
                    .with(MqConstant.ORDER_REMIND_ROUTING_KEY)
                    .noargs();
        }
    }

}
