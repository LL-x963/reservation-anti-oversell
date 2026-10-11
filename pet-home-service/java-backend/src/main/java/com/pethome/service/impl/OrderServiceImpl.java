package com.pethome.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.constant.ErrorCodeConstant;
import com.pethome.common.constant.MqConstant;
import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.context.UserContext;
import com.pethome.common.enums.OrderStatus;
import com.pethome.common.enums.TimeSlot;
import com.pethome.common.exception.BusinessException;
import com.pethome.config.OrderProperties;
import com.pethome.dto.OrderAdminPageQuery;
import com.pethome.dto.OrderAssignRequest;
import com.pethome.dto.OrderCommentRequest;
import com.pethome.dto.OrderItemRequest;
import com.pethome.dto.OrderPageQuery;
import com.pethome.dto.OrderPayRequest;
import com.pethome.dto.OrderSubmitRequest;
import com.pethome.entity.AddressBook;
import com.pethome.entity.Beautician;
import com.pethome.entity.BeauticianSchedule;
import com.pethome.entity.OrderComment;
import com.pethome.entity.OrderDetail;
import com.pethome.entity.PetProfile;
import com.pethome.entity.ServiceItem;
import com.pethome.entity.ServiceOrder;
import com.pethome.entity.User;
import com.pethome.mapper.AddressBookMapper;
import com.pethome.mapper.BeauticianScheduleMapper;
import com.pethome.mapper.OrderCommentMapper;
import com.pethome.mapper.OrderDetailMapper;
import com.pethome.mapper.ServiceOrderMapper;
import com.pethome.mq.MqMessageSender;
import com.pethome.service.AdminOrderService;
import com.pethome.service.BeauticianScoreService;
import com.pethome.service.BeauticianService;
import com.pethome.service.CartService;
import com.pethome.service.OrderService;
import com.pethome.service.PetProfileService;
import com.pethome.service.ServiceItemService;
import com.pethome.service.UserService;
import com.pethome.util.OrderNumberGenerator;
import com.pethome.util.AddressUtil;
import com.pethome.util.RedisUtil;
import com.pethome.vo.OrderCommentVO;
import com.pethome.vo.OrderDetailVO;
import com.pethome.vo.OrderItemVO;
import com.pethome.vo.OrderSubmitVO;
import com.pethome.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单服务实现（用户端 + 管理端 + 系统侧）
 *
 * @author pet-home
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<ServiceOrderMapper, ServiceOrder>
        implements OrderService, AdminOrderService {

    /** 排班可接单 */
    private static final int SCHEDULE_AVAILABLE = 1;
    /** 服务上架 */
    private static final int SERVICE_ON_SALE = 1;
    /** 美容师在职 */
    private static final int BEAUTICIAN_ON_DUTY = 1;

    /** 提醒状态：未提醒 */
    private static final int REMINDER_STATUS_NONE = 0;
    /** 提醒状态：已提醒 */
    private static final int REMINDER_STATUS_REMINDED = 1;
    /** 提醒状态：已取消 */
    private static final int REMINDER_STATUS_CANCELED = 2;

    /** 催单限流时长（秒） */
    private static final int REMINDER_LIMIT_SECONDS = 300;

    private final ServiceItemService serviceItemService;
    private final PetProfileService petProfileService;
    private final BeauticianService beauticianService;
    private final CartService cartService;
    private final BeauticianScoreService beauticianScoreService;
    private final AddressBookMapper addressBookMapper;
    private final BeauticianScheduleMapper beauticianScheduleMapper;
    private final OrderDetailMapper orderDetailMapper;
    private final OrderCommentMapper orderCommentMapper;
    private final MqMessageSender mqMessageSender;
    private final RedisUtil redisUtil;
    private final OrderProperties orderProperties;
    private final UserService userService;

    // ==================== 提交预约 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitVO submit(OrderSubmitRequest request) {
        Long userId = UserContext.requireUserId();

        // 1. 时段合法性校验
        String timeSlot = request.getTimeSlot().trim();
        if (!TimeSlot.isValid(timeSlot)) {
            throw new BusinessException(ErrorCodeConstant.SLOT_NOT_AVAILABLE,
                    "非法时段：" + request.getTimeSlot() + "，可选值：" + TimeSlot.allValuesAsText());
        }

        // 2. 关联数据校验：地址、宠物（须归属当前用户）、美容师
        checkAddress(request.getAddressId(), userId);
        checkPet(request.getPetId(), userId);
        checkBeautician(request.getBeauticianId());

        // 3. 服务项校验（存在且上架）并按下单快照计算总金额
        List<OrderDetail> details = new ArrayList<>(request.getItems().size());
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItemRequest item : request.getItems()) {
            ServiceItem serviceItem = getOnSaleService(item.getServiceId());
            BigDecimal amount = serviceItem.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            OrderDetail detail = new OrderDetail();
            detail.setServiceId(serviceItem.getId());
            detail.setName(serviceItem.getName());
            detail.setPrice(serviceItem.getPrice());
            detail.setQuantity(item.getQuantity());
            detail.setAmount(amount);
            details.add(detail);
            totalAmount = totalAmount.add(amount);
        }

        // 4. 排班校验：该美容师 + 日期 + 时段必须可接单
        BeauticianSchedule schedule = beauticianScheduleMapper.selectOne(new LambdaQueryWrapper<BeauticianSchedule>()
                .eq(BeauticianSchedule::getBeauticianId, request.getBeauticianId())
                .eq(BeauticianSchedule::getDate, request.getAppointmentDate())
                .eq(BeauticianSchedule::getTimeSlot, timeSlot));
        if (schedule == null || !Integer.valueOf(SCHEDULE_AVAILABLE).equals(schedule.getAvailable())) {
            throw new BusinessException(ErrorCodeConstant.SLOT_NOT_AVAILABLE, "该时段不可约");
        }

        // 5. 创建订单，初始状态待支付
        ServiceOrder order = new ServiceOrder();
        order.setOrderNumber(OrderNumberGenerator.generate());
        order.setUserId(userId);
        order.setAddressId(request.getAddressId());
        order.setPetId(request.getPetId());
        order.setBeauticianId(request.getBeauticianId());
        order.setAppointmentDate(request.getAppointmentDate());
        order.setTimeSlot(timeSlot);
        // 备注选填：空白统一存 null，避免出现无意义空串
        order.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.PENDING_PAY.getCode());
        order.setReminderStatus(REMINDER_STATUS_NONE);
        save(order);

        // 6. 并发防超卖：CAS 更新排班，影响行数为 0 说明已被他人抢占
        int affected = beauticianScheduleMapper.occupySlot(schedule.getId(), order.getId());
        if (affected == 0) {
            throw new BusinessException(ErrorCodeConstant.SLOT_ALREADY_BOOKED, "该时段已被预约");
        }

        // 7. 回写排班 id 与超时取消消息 id
        ServiceOrder scheduleUpdate = new ServiceOrder();
        scheduleUpdate.setId(order.getId());
        scheduleUpdate.setScheduleId(schedule.getId());
        scheduleUpdate.setMessageId(MqConstant.CANCEL_MESSAGE_ID_PREFIX + order.getId());
        updateById(scheduleUpdate);

        // 8. 保存订单明细
        for (OrderDetail detail : details) {
            detail.setOrderId(order.getId());
            orderDetailMapper.insert(detail);
        }

        // 9. 发送超时取消延迟消息（默认 30 分钟，可通过 pethome.order.cancel-delay-millis 调整）
        mqMessageSender.sendOrderCancelDelayed(order.getId(), orderProperties.getCancelDelayMillis());

        // 10. 下单成功后清空用户预约车
        cartService.clearCart(userId);

        log.info("提交预约成功: orderId={}, orderNumber={}, userId={}, beauticianId={}, date={}, timeSlot={}, totalAmount={}",
                order.getId(), order.getOrderNumber(), userId, request.getBeauticianId(),
                request.getAppointmentDate(), timeSlot, totalAmount);

        return new OrderSubmitVO(order.getId(), order.getOrderNumber(), totalAmount, order.getStatus());
    }

    // ==================== 支付 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(OrderPayRequest request) {
        Long userId = UserContext.requireUserId();
        ServiceOrder order = getOwnedOrder(request.getOrderId(), userId);

        if (!OrderStatus.PENDING_PAY.is(order.getStatus())) {
            throw new BusinessException("订单状态为" + OrderStatus.descOf(order.getStatus()) + "，不能支付");
        }

        ServiceOrder update = new ServiceOrder();
        update.setId(order.getId());
        update.setStatus(OrderStatus.PENDING_CONFIRM.getCode());
        update.setPayTime(LocalDateTime.now());
        updateById(update);

        log.info("模拟支付成功: orderId={}, userId={}", order.getId(), userId);
    }

    // ==================== 查询 ====================

    @Override
    public IPage<OrderVO> pageMyOrders(OrderPageQuery query) {
        Long userId = UserContext.requireUserId();
        Page<ServiceOrder> page = page(new Page<>(query.getPage(), query.getPageSize()),
                new LambdaQueryWrapper<ServiceOrder>()
                        .eq(ServiceOrder::getUserId, userId)
                        .eq(query.getStatus() != null, ServiceOrder::getStatus, query.getStatus())
                        .orderByDesc(ServiceOrder::getId));
        return toVOPage(page);
    }

    @Override
    public OrderDetailVO detailForUser(Long orderId) {
        Long userId = UserContext.requireUserId();
        return buildDetail(getOwnedOrder(orderId, userId));
    }

    @Override
    public IPage<OrderVO> pageForAdmin(OrderAdminPageQuery query) {
        Page<ServiceOrder> page = page(new Page<>(query.getPage(), query.getPageSize()),
                new LambdaQueryWrapper<ServiceOrder>()
                        .eq(query.getStatus() != null, ServiceOrder::getStatus, query.getStatus())
                        .eq(query.getAppointmentDate() != null, ServiceOrder::getAppointmentDate, query.getAppointmentDate())
                        .eq(query.getBeauticianId() != null, ServiceOrder::getBeauticianId, query.getBeauticianId())
                        .orderByDesc(ServiceOrder::getId));
        return toVOPage(page);
    }

    @Override
    public OrderDetailVO detailForAdmin(Long orderId) {
        return buildDetail(requireOrder(orderId));
    }

    // ==================== 取消 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByUser(Long orderId) {
        Long userId = UserContext.requireUserId();
        doCancel(getOwnedOrder(orderId, userId), "用户取消");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByUser(Long orderId) {
        Long userId = UserContext.requireUserId();
        ServiceOrder order = getOwnedOrder(orderId, userId);
        // 只允许删除已取消订单，避免用户在服务进行中把记录删掉
        if (!Integer.valueOf(OrderStatus.CANCELED.getCode()).equals(order.getStatus())) {
            throw new BusinessException("只能删除已取消的订单");
        }
        orderDetailMapper.delete(new LambdaQueryWrapper<OrderDetail>()
                .eq(OrderDetail::getOrderId, orderId));
        removeById(orderId);
        log.info("删除已取消订单成功: orderId={}, userId={}", orderId, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelByAdmin(Long orderId) {
        doCancel(requireOrder(orderId), "管理端取消");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelBySystem(Long orderId, String reason) {
        ServiceOrder order = getById(orderId);
        if (order == null) {
            log.warn("超时取消消息丢弃：订单不存在, orderId={}", orderId);
            return;
        }
        // 消费端先校验订单状态，非待支付直接丢弃
        if (!OrderStatus.PENDING_PAY.is(order.getStatus())) {
            log.info("超时取消消息丢弃：订单状态={}, orderId={}", OrderStatus.descOf(order.getStatus()), orderId);
            return;
        }
        doCancel(order, reason);
    }

    // ==================== 催单 ====================

    @Override
    public void remind(Long orderId) {
        Long userId = UserContext.requireUserId();
        ServiceOrder order = getOwnedOrder(orderId, userId);

        // 1. Redis 限流：key = reminder:{orderId}:{userId}，TTL 300 秒
        String limitKey = RedisKeyConstant.reminderKey(orderId, userId);
        boolean firstRequest = redisUtil.setIfAbsent(limitKey, 1, REMINDER_LIMIT_SECONDS, TimeUnit.SECONDS);
        if (!firstRequest) {
            throw new BusinessException(ErrorCodeConstant.REMINDER_TOO_FREQUENT, "催单过于频繁，请稍后再试");
        }

        // 2. 状态校验：仅待确认、待上门可催单；不满足时释放限流额度，避免误伤正常请求
        OrderStatus status = OrderStatus.fromCode(order.getStatus());
        if (status != OrderStatus.PENDING_CONFIRM && status != OrderStatus.PENDING_VISIT) {
            redisUtil.delete(limitKey);
            throw new BusinessException("订单状态为" + status.getDesc() + "，不能催单");
        }

        // 3. 发送即时催单通知
        mqMessageSender.sendOrderReminderNotify(orderId);
        log.info("用户催单成功: orderId={}, userId={}", orderId, userId);
    }

    // ==================== 确认 / 分配 / 完成 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long orderId) {
        ServiceOrder order = requireOrder(orderId);
        if (!OrderStatus.PENDING_CONFIRM.is(order.getStatus())) {
            throw new BusinessException("订单状态为" + OrderStatus.descOf(order.getStatus()) + "，不能确认");
        }

        ServiceOrder update = new ServiceOrder();
        update.setId(orderId);
        update.setStatus(OrderStatus.PENDING_VISIT.getCode());
        update.setMessageId(MqConstant.REMIND_MESSAGE_ID_PREFIX + orderId);
        updateById(update);

        // 计算预约开始时间前 1 小时，发送到点提醒延迟消息（已不足 1 小时则立即投递）
        LocalDateTime remindAt = LocalDateTime.of(order.getAppointmentDate(), TimeSlot.startTimeOf(order.getTimeSlot()))
                .minusHours(1);
        long delayMillis = Duration.between(LocalDateTime.now(), remindAt).toMillis();
        mqMessageSender.sendOrderRemindDelayed(orderId, delayMillis);

        log.info("确认订单成功: orderId={}, 提醒延迟={}ms", orderId, delayMillis);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assign(OrderAssignRequest request) {
        ServiceOrder order = requireOrder(request.getOrderId());
        OrderStatus status = OrderStatus.fromCode(order.getStatus());
        if (status != OrderStatus.PENDING_CONFIRM && status != OrderStatus.PENDING_VISIT) {
            throw new BusinessException("订单状态为" + status.getDesc() + "，不能分配美容师");
        }
        if (beauticianService.getById(request.getBeauticianId()) == null) {
            throw new BusinessException("美容师不存在");
        }

        ServiceOrder update = new ServiceOrder();
        update.setId(order.getId());
        update.setBeauticianId(request.getBeauticianId());
        updateById(update);

        // 仅调整订单归属，排班占用保持不变（不释放原排班、不占用新排班）
        log.info("分配美容师成功: orderId={}, beauticianId={}", order.getId(), request.getBeauticianId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long orderId) {
        ServiceOrder order = requireOrder(orderId);
        OrderStatus status = OrderStatus.fromCode(order.getStatus());
        if (status != OrderStatus.PENDING_VISIT && status != OrderStatus.IN_SERVICE) {
            throw new BusinessException("订单状态为" + status.getDesc() + "，不能完成");
        }

        ServiceOrder update = new ServiceOrder();
        update.setId(orderId);
        update.setStatus(OrderStatus.PENDING_COMMENT.getCode());
        updateById(update);

        log.info("完成订单成功: orderId={}, 状态置为待评价", orderId);
    }

    // ==================== 提醒消息处理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markReminded(Long orderId) {
        ServiceOrder order = getById(orderId);
        if (order == null || !OrderStatus.PENDING_VISIT.is(order.getStatus())) {
            log.info("到点提醒消息丢弃: orderId={}, 当前状态={}", orderId,
                    order == null ? "订单不存在" : OrderStatus.descOf(order.getStatus()));
            return;
        }

        ServiceOrder update = new ServiceOrder();
        update.setId(orderId);
        update.setReminderStatus(REMINDER_STATUS_REMINDED);
        updateById(update);

        log.info("预约到点提醒已发出: orderId={}", orderId);
    }

    // ==================== 评价 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void comment(OrderCommentRequest request) {
        Long userId = UserContext.requireUserId();
        ServiceOrder order = getOwnedOrder(request.getOrderId(), userId);

        if (!OrderStatus.PENDING_COMMENT.is(order.getStatus())) {
            throw new BusinessException("订单状态为" + OrderStatus.descOf(order.getStatus()) + "，不能评价");
        }
        Long commented = orderCommentMapper.selectCount(new LambdaQueryWrapper<OrderComment>()
                .eq(OrderComment::getOrderId, order.getId()));
        if (commented != null && commented > 0) {
            throw new BusinessException("该订单已评价");
        }

        OrderComment comment = new OrderComment();
        comment.setOrderId(order.getId());
        comment.setUserId(userId);
        comment.setBeauticianId(order.getBeauticianId());
        comment.setScore(request.getScore());
        comment.setContent(request.getContent());
        orderCommentMapper.insert(comment);

        // 评价完成，订单置为已完成
        ServiceOrder update = new ServiceOrder();
        update.setId(order.getId());
        update.setStatus(OrderStatus.COMPLETED.getCode());
        updateById(update);

        // 事务提交后异步重算该美容师平均评分（避免异步线程读不到未提交的评价数据）
        Long beauticianId = order.getBeauticianId();
        if (beauticianId != null) {
            afterCommit(() -> beauticianScoreService.recalculate(beauticianId));
        }

        log.info("订单评价成功: orderId={}, userId={}, score={}", order.getId(), userId, request.getScore());
    }

    // ==================== 内部方法 ====================

    /**
     * 取消订单：校验状态 → 置为已取消 → 释放排班 → 设置提醒状态为已取消
     */
    private void doCancel(ServiceOrder order, String reason) {
        OrderStatus status = OrderStatus.fromCode(order.getStatus());
        if (status == OrderStatus.COMPLETED || status == OrderStatus.CANCELED) {
            throw new BusinessException("订单已" + status.getDesc() + "，不能取消");
        }

        ServiceOrder update = new ServiceOrder();
        update.setId(order.getId());
        update.setStatus(OrderStatus.CANCELED.getCode());
        update.setReminderStatus(REMINDER_STATUS_CANCELED);
        updateById(update);

        // 释放排班：available=1，order_id=null
        int released = 0;
        if (order.getScheduleId() != null) {
            released = beauticianScheduleMapper.releaseSlot(order.getScheduleId(), order.getId());
        } else {
            released = beauticianScheduleMapper.releaseByOrderId(order.getId());
        }

        log.info("取消订单成功: orderId={}, reason={}, 释放排班数={}", order.getId(), reason, released);
    }

    private void checkAddress(Long addressId, Long userId) {
        if (addressBookMapper.countByIdAndUserId(addressId, userId) <= 0) {
            throw new BusinessException("地址不存在");
        }
    }

    private void checkPet(Long petId, Long userId) {
        PetProfile pet = petProfileService.getById(petId);
        if (pet == null || !Objects.equals(pet.getUserId(), userId)) {
            throw new BusinessException("宠物不存在");
        }
    }

    private void checkBeautician(Long beauticianId) {
        Beautician beautician = beauticianService.getById(beauticianId);
        if (beautician == null) {
            throw new BusinessException("美容师不存在");
        }
        if (!Integer.valueOf(BEAUTICIAN_ON_DUTY).equals(beautician.getStatus())) {
            throw new BusinessException("美容师已离职，无法预约");
        }
    }

    private ServiceItem getOnSaleService(Long serviceId) {
        ServiceItem serviceItem = serviceItemService.getById(serviceId);
        if (serviceItem == null) {
            throw new BusinessException("服务不存在");
        }
        if (!Integer.valueOf(SERVICE_ON_SALE).equals(serviceItem.getStatus())) {
            throw new BusinessException("服务已下架");
        }
        return serviceItem;
    }

    private ServiceOrder getOwnedOrder(Long orderId, Long userId) {
        ServiceOrder order = getById(orderId);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }

    private ServiceOrder requireOrder(Long orderId) {
        ServiceOrder order = getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        return order;
    }

    private IPage<OrderVO> toVOPage(Page<ServiceOrder> page) {
        List<ServiceOrder> records = page.getRecords();
        // 美容师 / 宠物 / 用户名称统一批量查询，避免 N+1
        Map<Long, String> beauticianNames = getNameMap(records.stream()
                .map(ServiceOrder::getBeauticianId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        Map<Long, String> petNames = getPetNameMap(records.stream()
                .map(ServiceOrder::getPetId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        Map<Long, String> userNames = getUserNameMap(records.stream()
                .map(ServiceOrder::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
        // 服务明细同样批量查询（一页一次），避免 N+1
        Map<Long, List<OrderItemVO>> itemsMap = getOrderItemsMap(records.stream()
                .map(ServiceOrder::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
        return page.convert(order -> toVO(order, beauticianNames, petNames, userNames, itemsMap));
    }

    /**
     * 批量查询订单明细：orderId -> 明细列表
     */
    private Map<Long, List<OrderItemVO>> getOrderItemsMap(List<Long> orderIds) {
        if (CollectionUtils.isEmpty(orderIds)) {
            return Collections.emptyMap();
        }
        return orderDetailMapper.selectList(new LambdaQueryWrapper<OrderDetail>()
                        .in(OrderDetail::getOrderId, orderIds))
                .stream()
                .collect(Collectors.groupingBy(OrderDetail::getOrderId,
                        Collectors.mapping(this::toItemVO, Collectors.toList())));
    }

    private OrderVO toVO(ServiceOrder order, Map<Long, String> beauticianNames,
                         Map<Long, String> petNames, Map<Long, String> userNames,
                         Map<Long, List<OrderItemVO>> itemsMap) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNumber(order.getOrderNumber());
        vo.setUserId(order.getUserId());
        vo.setUserName(order.getUserId() == null ? null : userNames.get(order.getUserId()));
        vo.setPetId(order.getPetId());
        vo.setPetName(order.getPetId() == null ? null : petNames.get(order.getPetId()));
        vo.setBeauticianId(order.getBeauticianId());
        vo.setBeauticianName(order.getBeauticianId() == null ? null : beauticianNames.get(order.getBeauticianId()));
        vo.setAppointmentDate(order.getAppointmentDate());
        vo.setTimeSlot(order.getTimeSlot());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(OrderStatus.descOf(order.getStatus()));
        vo.setCreateTime(order.getCreateTime());
        vo.setItems(itemsMap.getOrDefault(order.getId(), Collections.emptyList()));
        return vo;
    }

    private OrderDetailVO buildDetail(ServiceOrder order) {
        OrderDetailVO vo = new OrderDetailVO();
        BeanUtils.copyProperties(order, vo);
        vo.setStatusDesc(OrderStatus.descOf(order.getStatus()));
        if (order.getBeauticianId() != null) {
            Beautician beautician = beauticianService.getById(order.getBeauticianId());
            vo.setBeauticianName(beautician == null ? null : beautician.getName());
        }
        fillUserAndPet(vo, order);
        fillAddress(vo, order.getAddressId());

        vo.setItems(orderDetailMapper.selectList(new LambdaQueryWrapper<OrderDetail>()
                        .eq(OrderDetail::getOrderId, order.getId()))
                .stream()
                .map(this::toItemVO)
                .collect(Collectors.toList()));

        OrderComment comment = orderCommentMapper.selectOne(new LambdaQueryWrapper<OrderComment>()
                .eq(OrderComment::getOrderId, order.getId()));
        if (comment != null) {
            OrderCommentVO commentVO = new OrderCommentVO();
            commentVO.setScore(comment.getScore());
            commentVO.setContent(comment.getContent());
            commentVO.setCreateTime(comment.getCreateTime());
            vo.setComment(commentVO);
        }
        return vo;
    }

    private OrderItemVO toItemVO(OrderDetail detail) {
        OrderItemVO vo = new OrderItemVO();
        vo.setServiceId(detail.getServiceId());
        vo.setName(detail.getName());
        vo.setPrice(detail.getPrice());
        vo.setQuantity(detail.getQuantity());
        vo.setAmount(detail.getAmount());
        return vo;
    }

    private Map<Long, String> getNameMap(List<Long> beauticianIds) {
        if (CollectionUtils.isEmpty(beauticianIds)) {
            return Collections.emptyMap();
        }
        return beauticianService.listByIds(beauticianIds).stream()
                .collect(Collectors.toMap(Beautician::getId, Beautician::getName, (a, b) -> a));
    }

    /**
     * 宠物名映射（批量查询；名称为空时按 null 处理，不做过滤）
     */
    private Map<Long, String> getPetNameMap(List<Long> petIds) {
        Map<Long, String> names = new HashMap<>();
        if (CollectionUtils.isEmpty(petIds)) {
            return names;
        }
        petProfileService.listByIds(petIds).forEach(pet -> names.put(pet.getId(), pet.getName()));
        return names;
    }

    /**
     * 用户名映射（批量查询）
     */
    private Map<Long, String> getUserNameMap(List<Long> userIds) {
        Map<Long, String> names = new HashMap<>();
        if (CollectionUtils.isEmpty(userIds)) {
            return names;
        }
        userService.listByIds(userIds).forEach(user -> names.put(user.getId(), displayUserName(user)));
        return names;
    }

    /**
     * 用户展示名：优先姓名，姓名为空（微信用户未完善资料）时回退手机号
     */
    private String displayUserName(User user) {
        if (user == null) {
            return null;
        }
        return StringUtils.hasText(user.getName()) ? user.getName().trim() : user.getPhone();
    }

    /**
     * 详情填充用户名与宠物名（与列表口径保持一致）
     */
    private void fillUserAndPet(OrderDetailVO vo, ServiceOrder order) {
        if (order.getUserId() != null) {
            vo.setUserName(displayUserName(userService.getById(order.getUserId())));
        }
        if (order.getPetId() != null) {
            PetProfile pet = petProfileService.getById(order.getPetId());
            vo.setPetName(pet == null ? null : pet.getName());
        }
    }

    /**
     * 详情填充收货信息
     *
     * <p>地址被删除时静默跳过，保证历史订单详情仍可正常查看。</p>
     */
    private void fillAddress(OrderDetailVO vo, Long addressId) {
        if (addressId == null) {
            return;
        }
        AddressBook address = addressBookMapper.selectById(addressId);
        if (address == null) {
            return;
        }
        vo.setConsignee(address.getConsignee());
        vo.setConsigneePhone(address.getPhone());
        vo.setAddress(AddressUtil.fullAddress(address));
    }

    /**
     * 事务提交后执行，避免异步线程读不到未提交数据
     */
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

}
