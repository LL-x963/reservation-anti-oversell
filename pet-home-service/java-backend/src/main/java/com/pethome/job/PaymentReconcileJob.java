package com.pethome.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pethome.common.enums.OrderStatus;
import com.pethome.entity.PaymentRecord;
import com.pethome.entity.ServiceOrder;
import com.pethome.mapper.PaymentRecordMapper;
import com.pethome.mapper.ServiceOrderMapper;
import com.pethome.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付对账任务。
 *
 * <p>回调与订单更新在同一事务中，正常情况下不会不一致；但网络重试、进程重启、
 * 人工改库等异常场景仍可能留下「渠道已收款、订单仍待支付」的脏数据。
 * 该任务定期扫描最近一天的已支付流水并补单，同时打印 error 日志以便人工核对渠道账单。</p>
 *
 * @author pet-home
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconcileJob {

    /** 单次最多处理的流水数，避免一次性拉全表 */
    private static final int BATCH_SIZE = 200;

    private final PaymentRecordMapper paymentRecordMapper;
    private final ServiceOrderMapper serviceOrderMapper;

    @Scheduled(fixedDelayString = "${pethome.payment.reconcile-interval-millis:300000}",
            initialDelayString = "${pethome.payment.reconcile-initial-delay-millis:60000}")
    public void reconcile() {
        LocalDateTime since = LocalDateTime.now().minusDays(1);
        List<PaymentRecord> paidRecords = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getStatus, PaymentService.STATUS_PAID)
                        .ge(PaymentRecord::getCreateTime, since)
                        .orderByAsc(PaymentRecord::getId)
                        .last("LIMIT " + BATCH_SIZE));

        int fixed = 0;
        for (PaymentRecord record : paidRecords) {
            ServiceOrder order = serviceOrderMapper.selectById(record.getOrderId());
            if (order == null) {
                // 订单已被删除（例如用户删除已取消订单、测试数据清理）：
                // 关闭流水避免每轮对账重复扫描并刷 error 日志，同时保留一条 warn 供人工核对
                PaymentRecord close = new PaymentRecord();
                close.setId(record.getId());
                close.setStatus(PaymentService.STATUS_CLOSED);
                paymentRecordMapper.updateById(close);
                log.warn("对账发现支付流水对应订单不存在，已关闭该流水: orderId={}, tradeNo={}",
                        record.getOrderId(), record.getTradeNo());
                continue;
            }
            if (!OrderStatus.PENDING_PAY.is(order.getStatus())) {
                continue;
            }
            LocalDateTime payTime = record.getCallbackTime() == null ? LocalDateTime.now() : record.getCallbackTime();
            if (serviceOrderMapper.payCas(order.getId(), payTime) > 0) {
                fixed++;
                log.error("对账补偿：渠道已收款但订单仍待支付，已补单: orderId={}, orderNumber={}, tradeNo={}",
                        order.getId(), order.getOrderNumber(), record.getTradeNo());
            }
        }
        if (fixed > 0) {
            log.error("对账完成，本次补偿 {} 笔订单，请核对支付渠道账单", fixed);
        }
    }

}
