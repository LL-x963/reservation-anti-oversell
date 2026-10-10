package com.pethome.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pethome.dto.OrderCommentRequest;
import com.pethome.dto.OrderPageQuery;
import com.pethome.dto.OrderPayRequest;
import com.pethome.dto.OrderSubmitRequest;
import com.pethome.vo.OrderDetailVO;
import com.pethome.vo.OrderSubmitVO;
import com.pethome.vo.OrderVO;

/**
 * 订单服务（用户端 + 系统侧）
 *
 * @author pet-home
 */
public interface OrderService {

    /**
     * 提交预约：校验关联数据与排班、CAS 占用时段、生成订单与明细、发送超时取消延迟消息、清空预约车
     */
    OrderSubmitVO submit(OrderSubmitRequest request);

    /**
     * 模拟支付：待支付 → 待确认，记录支付时间
     */
    void pay(OrderPayRequest request);

    /**
     * 分页查询当前用户订单
     */
    IPage<OrderVO> pageMyOrders(OrderPageQuery query);

    /**
     * 查询当前用户订单详情
     */
    OrderDetailVO detailForUser(Long orderId);

    /**
     * 用户取消订单：非终态可取消，释放排班
     */
    void cancelByUser(Long orderId);

    /**
     * 催单：Redis 限流 + 状态校验 + 发送即时通知
     */
    void remind(Long orderId);

    /**
     * 评价订单：待评价 → 已完成，异步重算美容师平均评分
     */
    void comment(OrderCommentRequest request);

    // ==================== 系统侧（MQ 消费调用） ====================

    /**
     * 待支付超时自动取消：订单状态非待支付时直接丢弃
     */
    void cancelBySystem(Long orderId, String reason);

    /**
     * 到点提醒消息处理：订单状态非待上门时直接丢弃，否则标记为已提醒
     */
    void markReminded(Long orderId);

}
