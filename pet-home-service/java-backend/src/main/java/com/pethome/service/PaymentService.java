package com.pethome.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pethome.common.context.UserContext;
import com.pethome.common.enums.OrderStatus;
import com.pethome.common.exception.BusinessException;
import com.pethome.config.PaymentProperties;
import com.pethome.entity.PaymentRecord;
import com.pethome.entity.ServiceOrder;
import com.pethome.mapper.PaymentRecordMapper;
import com.pethome.mapper.ServiceOrderMapper;
import com.pethome.payment.PaymentCallbackResult;
import com.pethome.payment.PaymentGateway;
import com.pethome.payment.PaymentPrepayCommand;
import com.pethome.payment.PaymentPrepayResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 支付服务：预下单、回调处理、流水记录。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>支付渠道通过 {@link PaymentGateway} 抽象，切换渠道不需要改订单逻辑；</li>
 *   <li>回调必须验签，且用原始报文验签；</li>
 *   <li>回调幂等由「渠道交易号唯一索引 + 订单状态条件更新」双重保证；</li>
 *   <li>回调金额必须与订单金额一致，防止篡改金额。</li>
 * </ul>
 *
 * @author pet-home
 */
@Slf4j
@Service
public class PaymentService {

    /** 流水状态：待支付 */
    public static final int STATUS_PENDING = 0;

    /** 流水状态：已支付 */
    public static final int STATUS_PAID = 1;

    /** 流水状态：已关闭 */
    public static final int STATUS_CLOSED = 2;

    /** 回调报文最多保留长度，避免大报文撑爆字段 */
    private static final int CALLBACK_BODY_MAX_LENGTH = 2000;

    private final PaymentRecordMapper paymentRecordMapper;
    private final ServiceOrderMapper serviceOrderMapper;
    private final PaymentProperties paymentProperties;
    private final Map<String, PaymentGateway> gateways;

    public PaymentService(PaymentRecordMapper paymentRecordMapper,
                          ServiceOrderMapper serviceOrderMapper,
                          PaymentProperties paymentProperties,
                          List<PaymentGateway> gatewayList) {
        this.paymentRecordMapper = paymentRecordMapper;
        this.serviceOrderMapper = serviceOrderMapper;
        this.paymentProperties = paymentProperties;
        this.gateways = gatewayList.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentGateway::channel, Function.identity()));
    }

    /**
     * 发起支付（C 端用户）
     *
     * <p>模拟渠道没有真实支付平台，这里直接按「渠道回调已到达」处理，
     * 走的是与真实渠道完全相同的落流水 + 条件更新逻辑。</p>
     *
     * @param orderId 订单 id
     * @return 支付参数（真实渠道返回给客户端唤起支付；模拟渠道用于提示）
     */
    @Transactional(rollbackFor = Exception.class)
    public PaymentPrepayResult pay(Long orderId) {
        Long userId = UserContext.requireUserId();
        ServiceOrder order = serviceOrderMapper.selectById(orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BusinessException("订单不存在");
        }
        OrderStatus status = OrderStatus.fromCode(order.getStatus());
        if (status != OrderStatus.PENDING_PAY) {
            throw new BusinessException("订单状态为" + status.getDesc() + "，不能支付");
        }

        PaymentGateway gateway = currentGateway();
        PaymentPrepayResult prepayResult = gateway.prepay(new PaymentPrepayCommand(
                order.getId(), order.getOrderNumber(), order.getTotalAmount(), "宠物上门服务"));

        if (prepayResult.mock()) {
            markPaid(order, gateway.channel(), "MOCK" + order.getOrderNumber(), order.getTotalAmount(), null);
        } else {
            recordPending(order, gateway.channel());
        }
        return prepayResult;
    }

    /**
     * 处理支付回调（渠道服务端调用，免登录接口）
     *
     * @param channel 渠道标识
     * @param headers 回调请求头（验签）
     * @param body    回调原始报文
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(String channel, Map<String, String> headers, String body) {
        PaymentGateway gateway = gateways.get(channel);
        if (gateway == null) {
            throw new BusinessException("不支持的支付渠道: " + channel);
        }
        PaymentCallbackResult callback = gateway.verifyCallback(headers, body);
        if (!callback.success()) {
            log.warn("支付回调为失败状态，忽略: orderNumber={}, tradeNo={}",
                    callback.orderNumber(), callback.tradeNo());
            return;
        }

        ServiceOrder order = serviceOrderMapper.selectOne(new LambdaQueryWrapper<ServiceOrder>()
                .eq(ServiceOrder::getOrderNumber, callback.orderNumber()));
        if (order == null) {
            log.error("支付回调对应订单不存在: orderNumber={}, tradeNo={}",
                    callback.orderNumber(), callback.tradeNo());
            throw new BusinessException("订单不存在");
        }
        // 金额校验：渠道实付金额必须与订单金额一致
        if (order.getTotalAmount().compareTo(callback.amount()) != 0) {
            log.error("支付回调金额与订单金额不一致: orderNumber={}, orderAmount={}, callbackAmount={}",
                    callback.orderNumber(), order.getTotalAmount(), callback.amount());
            throw new BusinessException("支付金额与订单金额不一致");
        }

        int affected = serviceOrderMapper.payCas(order.getId(), LocalDateTime.now());
        // 无论订单是否本次更新，都落流水；重复回调靠 uk_trade_no 幂等
        recordPaid(order, channel, callback.tradeNo(), callback.amount(), body);
        if (affected == 0) {
            log.info("重复支付回调，订单状态未变更（幂等）: orderNumber={}, tradeNo={}",
                    callback.orderNumber(), callback.tradeNo());
            return;
        }
        log.info("支付回调处理成功: orderNumber={}, tradeNo={}, amount={}",
                callback.orderNumber(), callback.tradeNo(), callback.amount());
    }

    /**
     * 标记支付成功：条件更新订单 + 落流水
     */
    private void markPaid(ServiceOrder order, String channel, String tradeNo, BigDecimal amount, String body) {
        int affected = serviceOrderMapper.payCas(order.getId(), LocalDateTime.now());
        recordPaid(order, channel, tradeNo, amount, body);
        if (affected == 0) {
            ServiceOrder latest = serviceOrderMapper.selectById(order.getId());
            if (latest == null || !OrderStatus.PENDING_PAY.is(latest.getStatus())) {
                // 已支付或已取消，按幂等处理；若流水与订单不一致由对账任务兜底
                log.info("订单状态已变更，支付按幂等处理: orderId={}, status={}",
                        order.getId(), latest == null ? "订单不存在" : latest.getStatus());
                return;
            }
            throw new BusinessException("订单状态已变更，请刷新后重试");
        }
        log.info("支付成功: orderId={}, orderNumber={}, channel={}, tradeNo={}",
                order.getId(), order.getOrderNumber(), channel, tradeNo);
    }

    /**
     * 记录已支付流水（渠道交易号唯一，重复回调不会产生多条）
     */
    private void recordPaid(ServiceOrder order, String channel, String tradeNo,
                            BigDecimal amount, String body) {
        boolean exists = paymentRecordMapper.exists(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getTradeNo, tradeNo));
        if (exists) {
            return;
        }
        PaymentRecord record = new PaymentRecord();
        record.setOrderId(order.getId());
        record.setOrderNumber(order.getOrderNumber());
        record.setChannel(channel);
        record.setTradeNo(tradeNo);
        record.setAmount(amount);
        record.setStatus(STATUS_PAID);
        record.setCallbackTime(LocalDateTime.now());
        if (StringUtils.isNotBlank(body)) {
            record.setCallbackBody(StringUtils.abbreviate(body, CALLBACK_BODY_MAX_LENGTH));
        }
        try {
            paymentRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发回调：另一请求已写入同一交易号
            log.info("支付流水已存在（并发回调）: tradeNo={}", tradeNo);
        }
    }

    /**
     * 真实渠道预下单后记录待支付流水；同一订单存在待支付流水时复用
     */
    private void recordPending(ServiceOrder order, String channel) {
        boolean exists = paymentRecordMapper.exists(new LambdaQueryWrapper<PaymentRecord>()
                .eq(PaymentRecord::getOrderId, order.getId())
                .eq(PaymentRecord::getStatus, STATUS_PENDING));
        if (exists) {
            return;
        }
        PaymentRecord record = new PaymentRecord();
        record.setOrderId(order.getId());
        record.setOrderNumber(order.getOrderNumber());
        record.setChannel(channel);
        record.setAmount(order.getTotalAmount());
        record.setStatus(STATUS_PENDING);
        paymentRecordMapper.insert(record);
    }

    /**
     * 当前生效的支付渠道
     */
    private PaymentGateway currentGateway() {
        PaymentGateway gateway = gateways.get(paymentProperties.getChannel());
        if (gateway == null) {
            throw new BusinessException("不支持的支付渠道: " + paymentProperties.getChannel());
        }
        return gateway;
    }

}
