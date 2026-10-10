package com.pethome.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pethome.dto.OrderAdminPageQuery;
import com.pethome.dto.OrderAssignRequest;
import com.pethome.vo.OrderDetailVO;
import com.pethome.vo.OrderVO;

/**
 * 订单服务（管理端）
 *
 * @author pet-home
 */
public interface AdminOrderService {

    /**
     * 分页查询订单，支持按状态、预约日期、美容师筛选
     */
    IPage<OrderVO> pageForAdmin(OrderAdminPageQuery query);

    /**
     * 查询订单详情
     */
    OrderDetailVO detailForAdmin(Long orderId);

    /**
     * 确认订单：待确认 → 待上门，并发送预约开始前 1 小时的提醒消息
     */
    void confirm(Long orderId);

    /**
     * 分配/改派美容师：仅调整订单归属，不改变排班占用
     */
    void assign(OrderAssignRequest request);

    /**
     * 管理端取消订单：非终态可取消，释放排班
     */
    void cancelByAdmin(Long orderId);

    /**
     * 完成订单：待上门/服务中 → 待评价
     */
    void complete(Long orderId);

}
