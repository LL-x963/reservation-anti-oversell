package com.pethome.mq;

import com.pethome.common.constant.MqConstant;
import com.pethome.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 订单消息消费端
 *
 * <p>1. 超时取消：订单非待支付状态直接丢弃；<br>
 * 2. 到点提醒：订单非待上门状态直接丢弃；<br>
 * 3. 催单通知：仅记录日志（实际项目可对接短信/公众号模板消息）。</p>
 *
 * @author pet-home
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final OrderService orderService;

    /**
     * 待支付超时自动取消（延迟 30 分钟）
     */
    @RabbitListener(queues = MqConstant.ORDER_CANCEL_QUEUE)
    public void onOrderCancelMessage(Long orderId) {
        log.info("收到订单超时取消消息: orderId={}", orderId);
        orderService.cancelBySystem(orderId, "待支付超时自动取消");
    }

    /**
     * 预约开始前 1 小时到点提醒
     */
    @RabbitListener(queues = MqConstant.ORDER_REMIND_QUEUE)
    public void onOrderRemindMessage(Long orderId) {
        log.info("收到预约到点提醒消息: orderId={}", orderId);
        orderService.markReminded(orderId);
    }

    /**
     * 用户催单即时通知
     */
    @RabbitListener(queues = MqConstant.ORDER_REMINDER_QUEUE)
    public void onOrderReminderMessage(Long orderId) {
        log.info("收到用户催单通知: orderId={}", orderId);
    }

}
