package com.pethome.service;

import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.context.UserContext;
import com.pethome.common.enums.OrderStatus;
import com.pethome.common.enums.UserType;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.CartAddRequest;
import com.pethome.dto.OrderAdminPageQuery;
import com.pethome.dto.OrderAssignRequest;
import com.pethome.dto.OrderCommentRequest;
import com.pethome.dto.OrderItemRequest;
import com.pethome.dto.OrderPageQuery;
import com.pethome.dto.OrderPayRequest;
import com.pethome.dto.OrderSubmitRequest;
import com.pethome.entity.AddressBook;
import com.pethome.entity.ServiceOrder;
import com.pethome.mapper.AddressBookMapper;
import com.pethome.mapper.BeauticianScheduleMapper;
import com.pethome.mapper.ServiceOrderMapper;
import com.pethome.util.RedisUtil;
import com.pethome.vo.OrderDetailVO;
import com.pethome.vo.OrderSubmitVO;
import com.pethome.vo.OrderVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 订单服务层测试（覆盖核心业务规则，事务回滚不污染数据库）
 *
 * @author pet-home
 */
@SpringBootTest
@Transactional
class OrderServiceTest {

    private static final String SLOT_10_12 = "10:00-12:00";
    private static final String SLOT_12_14 = "12:00-14:00";
    private static final LocalDate DATE = LocalDate.now().plusDays(3);
    private static final LocalDate OTHER_DATE = LocalDate.now().plusDays(4);

    @Autowired
    private OrderService orderService;

    @Autowired
    private AdminOrderService adminOrderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private BeauticianScheduleMapper beauticianScheduleMapper;

    @Autowired
    private ServiceOrderMapper serviceOrderMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long userId;
    private Long otherUserId;
    private Long addressId;
    private Long otherAddressId;
    private Long petId;
    private Long otherPetId;
    private Long beauticianId;
    private Long offDutyBeauticianId;
    private Long onSaleServiceId;
    private Long offlineServiceId;

    /** 测试过程中产生的 Redis 催单限流 key，便于清理 */
    private final List<String> reminderKeys = new ArrayList<>();

    @BeforeEach
    void setUp() {
        userId = insertUser("IT-ORDER-USER-A");
        otherUserId = insertUser("IT-ORDER-USER-B");
        addressId = insertAddress(userId, "IT-订单地址A");
        otherAddressId = insertAddress(otherUserId, "IT-订单地址B");
        petId = insertPet(userId, "IT-订单宠物A");
        otherPetId = insertPet(otherUserId, "IT-订单宠物B");

        beauticianId = insertBeautician("IT-订单美容师", 1);
        offDutyBeauticianId = insertBeautician("IT-订单离职美容师", 0);

        // 可接单时段
        insertSchedule(beauticianId, DATE, SLOT_10_12, 1, null);
        // 已被占用时段
        insertSchedule(beauticianId, DATE, SLOT_12_14, 0, 99999L);

        Long categoryId = insertCategory("IT-订单分类");
        onSaleServiceId = insertServiceItem(categoryId, "IT-订单服务A", "99.00", 1);
        offlineServiceId = insertServiceItem(categoryId, "IT-订单服务B", "50.00", 0);

        UserContext.set(userId, UserType.USER);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        // Redis 不受事务回滚影响，测试内自行清理
        redisUtil.delete(RedisKeyConstant.cartKey(userId));
        redisUtil.delete(RedisKeyConstant.cartKey(otherUserId));
        reminderKeys.forEach(redisUtil::delete);
        reminderKeys.clear();
    }

    // ==================== 提交预约 ====================

    @Test
    void submitShouldCreateOrderAndOccupySchedule() {
        OrderSubmitVO result = orderService.submit(submitRequest());

        assertThat(result.getOrderId()).isNotNull().isPositive();
        assertThat(result.getOrderNumber()).isNotBlank();
        assertThat(result.getTotalAmount()).isEqualByComparingTo("198.00");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING_PAY.getCode());

        ServiceOrder order = serviceOrderMapper.selectById(result.getOrderId());
        assertThat(order.getUserId()).isEqualTo(userId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAY.getCode());
        assertThat(order.getReminderStatus()).isZero();
        assertThat(order.getScheduleId()).isNotNull();
        assertThat(order.getMessageId()).isEqualTo("cancel:" + order.getId());

        // 订单明细按下单快照落库
        List<Map<String, Object>> details = jdbcTemplate.queryForList(
                "SELECT name, price, quantity, amount FROM order_detail WHERE order_id = ?", order.getId());
        assertThat(details).hasSize(1);
        assertThat(details.get(0).get("name")).isEqualTo("IT-订单服务A");
        assertThat(((BigDecimal) details.get(0).get("amount"))).isEqualByComparingTo("198.00");

        // 排班被占用
        Map<String, Object> schedule = jdbcTemplate.queryForMap(
                "SELECT available, order_id FROM beautician_schedule WHERE id = ?", order.getScheduleId());
        assertThat(((Number) schedule.get("available")).intValue()).isZero();
        assertThat(((Number) schedule.get("order_id")).longValue()).isEqualTo(order.getId());
    }

    @Test
    void submitShouldClearUserCart() {
        CartAddRequest cartAdd = new CartAddRequest();
        cartAdd.setServiceId(onSaleServiceId);
        cartAdd.setQuantity(1);
        cartService.add(cartAdd);
        assertThat(cartService.list()).hasSize(1);

        orderService.submit(submitRequest());

        assertThat(cartService.list()).isEmpty();
        assertThat(redisUtil.hasKey(RedisKeyConstant.cartKey(userId))).isFalse();
    }

    @Test
    void submitShouldRejectInvalidTimeSlot() {
        OrderSubmitRequest request = submitRequest();
        request.setTimeSlot("09:00-10:00");

        BusinessException exception = catchBusinessException(() -> orderService.submit(request));
        assertThat(exception.getCode()).isEqualTo(601);
        assertThat(exception.getMessage()).contains("非法时段");
    }

    @Test
    void submitShouldRejectUnavailableSchedule() {
        OrderSubmitRequest request = submitRequest();
        request.setTimeSlot(SLOT_12_14);

        BusinessException exception = catchBusinessException(() -> orderService.submit(request));
        assertThat(exception.getCode()).isEqualTo(601);
        assertThat(exception.getMessage()).isEqualTo("该时段不可约");
    }

    @Test
    void submitShouldRejectMissingSchedule() {
        OrderSubmitRequest request = submitRequest();
        request.setTimeSlot("14:00-16:00");

        BusinessException exception = catchBusinessException(() -> orderService.submit(request));
        assertThat(exception.getCode()).isEqualTo(601);
    }

    @Test
    void occupySlotShouldBeCasGuarded() {
        Long scheduleId = queryScheduleId(beauticianId, DATE, SLOT_10_12);
        Long firstOrderId = 900001L;

        // 首次占用成功
        assertThat(beauticianScheduleMapper.occupySlot(scheduleId, firstOrderId)).isEqualTo(1);
        // 并发再次占用：CAS 条件 available=1 不成立，影响行数为 0（对应 602 该时段已被预约）
        assertThat(beauticianScheduleMapper.occupySlot(scheduleId, 900002L)).isZero();
        assertThat(queryOrderId(scheduleId)).isEqualTo(firstOrderId);
    }

    @Test
    void submitShouldRejectNonexistentOrOfflineService() {
        OrderSubmitRequest nonexistent = submitRequest();
        nonexistent.setItems(List.of(item(99999999L, 1)));
        assertThat(catchBusinessException(() -> orderService.submit(nonexistent)).getMessage())
                .isEqualTo("服务不存在");

        OrderSubmitRequest offline = submitRequest();
        offline.setItems(List.of(item(offlineServiceId, 1)));
        assertThat(catchBusinessException(() -> orderService.submit(offline)).getMessage())
                .isEqualTo("服务已下架");
    }

    @Test
    void submitShouldRejectNonexistentOrNotOwnedPet() {
        OrderSubmitRequest nonexistent = submitRequest();
        nonexistent.setPetId(99999999L);
        assertThat(catchBusinessException(() -> orderService.submit(nonexistent)).getMessage())
                .isEqualTo("宠物不存在");

        OrderSubmitRequest othersPet = submitRequest();
        othersPet.setPetId(otherPetId);
        assertThat(catchBusinessException(() -> orderService.submit(othersPet)).getMessage())
                .isEqualTo("宠物不存在");
    }

    @Test
    void submitShouldRejectNonexistentOrNotOwnedAddress() {
        OrderSubmitRequest nonexistent = submitRequest();
        nonexistent.setAddressId(99999999L);
        assertThat(catchBusinessException(() -> orderService.submit(nonexistent)).getMessage())
                .isEqualTo("地址不存在");

        OrderSubmitRequest othersAddress = submitRequest();
        othersAddress.setAddressId(otherAddressId);
        assertThat(catchBusinessException(() -> orderService.submit(othersAddress)).getMessage())
                .isEqualTo("地址不存在");
    }

    @Test
    void submitShouldRejectNonexistentOrOffDutyBeautician() {
        OrderSubmitRequest nonexistent = submitRequest();
        nonexistent.setBeauticianId(99999999L);
        assertThat(catchBusinessException(() -> orderService.submit(nonexistent)).getMessage())
                .isEqualTo("美容师不存在");

        OrderSubmitRequest offDuty = submitRequest();
        offDuty.setBeauticianId(offDutyBeauticianId);
        assertThat(catchBusinessException(() -> orderService.submit(offDuty)).getMessage())
                .isEqualTo("美容师已离职，无法预约");
    }

    @Test
    void submitShouldSumMultipleItems() {
        OrderSubmitRequest request = submitRequest();
        request.setItems(List.of(item(onSaleServiceId, 2), item(insertServiceItem(queryCategoryId(), "IT-订单服务C", "10.50", 1), 3)));

        OrderSubmitVO result = orderService.submit(request);

        // 99.00 * 2 + 10.50 * 3 = 229.50
        assertThat(result.getTotalAmount()).isEqualByComparingTo("229.50");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM order_detail WHERE order_id = ?", Integer.class, result.getOrderId()))
                .isEqualTo(2);
    }

    // ==================== 支付 ====================

    @Test
    void payShouldMoveToPendingConfirm() {
        Long orderId = submit();

        orderService.pay(payRequest(orderId));

        ServiceOrder order = serviceOrderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM.getCode());
        assertThat(order.getPayTime()).isNotNull();
    }

    @Test
    void payShouldRejectNonPendingPayStatus() {
        Long orderId = submit();
        orderService.pay(payRequest(orderId));

        assertThat(catchBusinessException(() -> orderService.pay(payRequest(orderId))).getMessage())
                .contains("不能支付");
    }

    @Test
    void payShouldRejectOtherUsersOrder() {
        Long orderId = submit();
        UserContext.set(otherUserId, UserType.USER);

        assertThat(catchBusinessException(() -> orderService.pay(payRequest(orderId))).getMessage())
                .isEqualTo("订单不存在");
    }

    // ==================== 取消 ====================

    @Test
    void cancelShouldReleaseScheduleAndSetReminderStatus() {
        Long orderId = submit();
        Long scheduleId = serviceOrderMapper.selectById(orderId).getScheduleId();

        orderService.cancelByUser(orderId);

        ServiceOrder order = serviceOrderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED.getCode());
        assertThat(order.getReminderStatus()).isEqualTo(2);

        Map<String, Object> schedule = jdbcTemplate.queryForMap(
                "SELECT available, order_id FROM beautician_schedule WHERE id = ?", scheduleId);
        assertThat(((Number) schedule.get("available")).intValue()).isEqualTo(1);
        assertThat(schedule.get("order_id")).isNull();
    }

    @Test
    void cancelShouldRejectCompletedAndCanceledOrder() {
        Long orderId = submit();
        jdbcTemplate.update("UPDATE service_order SET status = 5 WHERE id = ?", orderId);
        assertThat(catchBusinessException(() -> orderService.cancelByUser(orderId)).getMessage())
                .contains("不能取消");

        jdbcTemplate.update("UPDATE service_order SET status = 6 WHERE id = ?", orderId);
        assertThat(catchBusinessException(() -> orderService.cancelByUser(orderId)).getMessage())
                .contains("不能取消");
    }

    @Test
    void cancelShouldRejectOtherUsersOrder() {
        Long orderId = submit();
        UserContext.set(otherUserId, UserType.USER);

        assertThat(catchBusinessException(() -> orderService.cancelByUser(orderId)).getMessage())
                .isEqualTo("订单不存在");
    }

    @Test
    void cancelBySystemShouldCancelPendingPayOrder() {
        Long orderId = submit();

        orderService.cancelBySystem(orderId, "待支付超时自动取消");

        assertThat(serviceOrderMapper.selectById(orderId).getStatus()).isEqualTo(OrderStatus.CANCELED.getCode());
    }

    @Test
    void cancelBySystemShouldDiscardWhenOrderNotPendingPay() {
        Long orderId = submit();
        orderService.pay(payRequest(orderId));

        orderService.cancelBySystem(orderId, "待支付超时自动取消");

        // 非待支付状态直接丢弃，状态保持待确认
        assertThat(serviceOrderMapper.selectById(orderId).getStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM.getCode());
        // 订单不存在时同样直接丢弃，不抛异常
        orderService.cancelBySystem(99999999L, "待支付超时自动取消");
    }

    // ==================== 催单 ====================

    @Test
    void remindShouldLimitByRedis() {
        Long orderId = submit();
        orderService.pay(payRequest(orderId));

        orderService.remind(orderId);
        String limitKey = RedisKeyConstant.reminderKey(orderId, userId);
        reminderKeys.add(limitKey);
        assertThat(redisUtil.getExpire(limitKey)).isBetween(1L, 300L);

        BusinessException exception = catchBusinessException(() -> orderService.remind(orderId));
        assertThat(exception.getCode()).isEqualTo(603);
        assertThat(exception.getMessage()).isEqualTo("催单过于频繁，请稍后再试");
    }

    @Test
    void remindShouldRejectWrongStatusAndReleaseLimit() {
        Long orderId = submit();

        BusinessException exception = catchBusinessException(() -> orderService.remind(orderId));
        assertThat(exception.getMessage()).contains("不能催单");
        // 状态不满足时释放限流额度，避免误伤
        assertThat(redisUtil.hasKey(RedisKeyConstant.reminderKey(orderId, userId))).isFalse();
    }

    // ==================== 确认 / 分配 / 完成 ====================

    @Test
    void confirmShouldMoveToPendingVisitAndRecordRemindMessage() {
        Long orderId = submit();
        orderService.pay(payRequest(orderId));

        adminOrderService.confirm(orderId);

        ServiceOrder order = serviceOrderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_VISIT.getCode());
        assertThat(order.getMessageId()).isEqualTo("remind:" + orderId);
    }

    @Test
    void confirmShouldRejectWrongStatus() {
        Long orderId = submit();

        assertThat(catchBusinessException(() -> adminOrderService.confirm(orderId)).getMessage())
                .contains("不能确认");
    }

    @Test
    void assignShouldUpdateBeauticianAndValidateStatus() {
        Long orderId = submit();
        orderService.pay(payRequest(orderId));

        OrderAssignRequest request = new OrderAssignRequest();
        request.setOrderId(orderId);
        request.setBeauticianId(offDutyBeauticianId);
        adminOrderService.assign(request);

        assertThat(serviceOrderMapper.selectById(orderId).getBeauticianId()).isEqualTo(offDutyBeauticianId);

        // 不存在的美容师
        request.setBeauticianId(99999999L);
        assertThat(catchBusinessException(() -> adminOrderService.assign(request)).getMessage())
                .isEqualTo("美容师不存在");

        // 状态不允许（待支付）
        Long anotherOrderId = submit(OTHER_DATE);
        OrderAssignRequest pendingPay = new OrderAssignRequest();
        pendingPay.setOrderId(anotherOrderId);
        pendingPay.setBeauticianId(beauticianId);
        assertThat(catchBusinessException(() -> adminOrderService.assign(pendingPay)).getMessage())
                .contains("不能分配美容师");
    }

    @Test
    void completeShouldMoveToPendingComment() {
        Long orderId = submit();
        orderService.pay(payRequest(orderId));
        adminOrderService.confirm(orderId);

        adminOrderService.complete(orderId);

        assertThat(serviceOrderMapper.selectById(orderId).getStatus()).isEqualTo(OrderStatus.PENDING_COMMENT.getCode());

        // 再次完成应被拒绝
        assertThat(catchBusinessException(() -> adminOrderService.complete(orderId)).getMessage())
                .contains("不能完成");
    }

    @Test
    void completeShouldRejectWrongStatus() {
        Long orderId = submit();

        assertThat(catchBusinessException(() -> adminOrderService.complete(orderId)).getMessage())
                .contains("不能完成");
    }

    // ==================== 提醒消息处理 ====================

    @Test
    void markRemindedShouldOnlyHandlePendingVisitOrder() {
        Long orderId = submit();
        orderService.pay(payRequest(orderId));
        adminOrderService.confirm(orderId);

        orderService.markReminded(orderId);

        assertThat(serviceOrderMapper.selectById(orderId).getReminderStatus()).isEqualTo(1);

        // 非待上门状态直接丢弃
        orderService.cancelByUser(orderId);
        orderService.markReminded(orderId);
        assertThat(serviceOrderMapper.selectById(orderId).getReminderStatus()).isEqualTo(2);
    }

    // ==================== 评价 ====================

    @Test
    void commentShouldInsertCommentAndCompleteOrder() {
        Long orderId = submit();
        orderService.pay(payRequest(orderId));
        adminOrderService.confirm(orderId);
        adminOrderService.complete(orderId);

        orderService.comment(commentRequest(orderId, 4, "服务很好"));

        ServiceOrder order = serviceOrderMapper.selectById(orderId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED.getCode());

        Map<String, Object> comment = jdbcTemplate.queryForMap(
                "SELECT order_id, user_id, beautician_id, score, content FROM order_comment WHERE order_id = ?", orderId);
        assertThat(((Number) comment.get("beautician_id")).longValue()).isEqualTo(beauticianId);
        assertThat(((Number) comment.get("score")).intValue()).isEqualTo(4);
    }

    @Test
    void commentShouldRejectWrongStatusAndDuplicate() {
        Long orderId = submit();

        assertThat(catchBusinessException(() -> orderService.comment(commentRequest(orderId, 4, null))).getMessage())
                .contains("不能评价");

        orderService.pay(payRequest(orderId));
        adminOrderService.confirm(orderId);
        adminOrderService.complete(orderId);
        orderService.comment(commentRequest(orderId, 4, null));

        // 状态已变为已完成，不能重复评价
        assertThat(catchBusinessException(() -> orderService.comment(commentRequest(orderId, 5, null))).getMessage())
                .contains("不能评价");
    }

    @Test
    void commentShouldRejectOtherUsersOrder() {
        Long orderId = submit();
        orderService.pay(payRequest(orderId));
        adminOrderService.confirm(orderId);
        adminOrderService.complete(orderId);

        UserContext.set(otherUserId, UserType.USER);
        assertThat(catchBusinessException(() -> orderService.comment(commentRequest(orderId, 4, null))).getMessage())
                .isEqualTo("订单不存在");
    }

    // ==================== 查询 ====================

    @Test
    void pageMyOrdersShouldFilterByStatusAndIsolateUsers() {
        Long orderId = submit();

        OrderPageQuery pendingPay = new OrderPageQuery();
        pendingPay.setStatus(OrderStatus.PENDING_PAY.getCode());
        assertThat(orderService.pageMyOrders(pendingPay).getTotal()).isEqualTo(1);

        OrderPageQuery canceled = new OrderPageQuery();
        canceled.setStatus(OrderStatus.CANCELED.getCode());
        assertThat(orderService.pageMyOrders(canceled).getTotal()).isZero();

        UserContext.set(otherUserId, UserType.USER);
        assertThat(orderService.pageMyOrders(new OrderPageQuery()).getTotal()).isZero();
        assertThat(catchBusinessException(() -> orderService.detailForUser(orderId)).getMessage())
                .isEqualTo("订单不存在");
    }

    @Test
    void detailShouldReturnItemsAndComment() {
        Long orderId = submit();
        orderService.pay(payRequest(orderId));
        adminOrderService.confirm(orderId);
        adminOrderService.complete(orderId);
        orderService.comment(commentRequest(orderId, 5, "非常满意"));

        OrderDetailVO detail = orderService.detailForUser(orderId);

        assertThat(detail.getOrderNumber()).isNotBlank();
        assertThat(detail.getStatus()).isEqualTo(OrderStatus.COMPLETED.getCode());
        assertThat(detail.getStatusDesc()).isEqualTo("已完成");
        assertThat(detail.getBeauticianName()).isEqualTo("IT-订单美容师");
        assertThat(detail.getItems()).hasSize(1);
        assertThat(detail.getItems().get(0).getName()).isEqualTo("IT-订单服务A");
        assertThat(detail.getComment()).isNotNull();
        assertThat(detail.getComment().getScore()).isEqualTo(5);
    }

    // ==================== 管理端查询（用户名 / 宠物名 / 收货信息） ====================

    @Test
    void adminPageShouldResolveUserNameAndPetName() {
        jdbcTemplate.update("UPDATE `user` SET name = ?, phone = ? WHERE id = ?", "IT-订单用户A", "13900000001", userId);
        Long orderId = submit();

        OrderAdminPageQuery query = new OrderAdminPageQuery();
        query.setBeauticianId(beauticianId);
        List<OrderVO> records = adminOrderService.pageForAdmin(query).getRecords();

        assertThat(records).hasSize(1);
        OrderVO vo = records.get(0);
        assertThat(vo.getId()).isEqualTo(orderId);
        assertThat(vo.getUserName()).isEqualTo("IT-订单用户A");
        assertThat(vo.getPetName()).isEqualTo("IT-订单宠物A");
        assertThat(vo.getBeauticianName()).isEqualTo("IT-订单美容师");

        // 详情与列表口径一致
        OrderDetailVO detail = adminOrderService.detailForAdmin(orderId);
        assertThat(detail.getUserName()).isEqualTo("IT-订单用户A");
        assertThat(detail.getPetName()).isEqualTo("IT-订单宠物A");
    }

    @Test
    void adminQueryShouldFallbackUserNameToPhone() {
        // 微信用户可能未完善姓名，回退手机号
        jdbcTemplate.update("UPDATE `user` SET name = NULL, phone = ? WHERE id = ?", "13900000002", userId);
        submit();

        OrderAdminPageQuery query = new OrderAdminPageQuery();
        query.setBeauticianId(beauticianId);
        assertThat(adminOrderService.pageForAdmin(query).getRecords().get(0).getUserName())
                .isEqualTo("13900000002");
    }

    @Test
    void adminDetailShouldReturnConsigneeAndFullAddress() {
        Long orderId = submit();

        OrderDetailVO detail = adminOrderService.detailForAdmin(orderId);
        assertThat(detail.getAddressId()).isEqualTo(addressId);
        assertThat(detail.getConsignee()).isEqualTo("IT-订单地址A");
        assertThat(detail.getConsigneePhone()).isEqualTo("13800000000");
        // 未填省市区时只拼接详细地址
        assertThat(detail.getAddress()).isEqualTo("IT-详细地址");

        // 补齐省市区后按「省 + 市 + 区 + 详细地址」拼接
        // 注意：必须走 MyBatis 更新（同一 SqlSession 会清理一级缓存），
        // 用 jdbcTemplate 旁路更新会导致随后 selectById 命中旧的一级缓存
        AddressBook withRegion = new AddressBook();
        withRegion.setId(addressId);
        withRegion.setProvinceName("浙江省");
        withRegion.setCityName("杭州市");
        withRegion.setDistrictName("西湖区");
        addressBookMapper.updateById(withRegion);
        assertThat(adminOrderService.detailForAdmin(orderId).getAddress())
                .isEqualTo("浙江省杭州市西湖区IT-详细地址");
    }

    @Test
    void adminDetailShouldTolerateDeletedAddress() {
        Long orderId = submit();
        jdbcTemplate.update("DELETE FROM address_book WHERE id = ?", addressId);

        OrderDetailVO detail = adminOrderService.detailForAdmin(orderId);

        // 地址被删除时不抛异常：收货字段留空，其余信息照常返回
        assertThat(detail.getConsignee()).isNull();
        assertThat(detail.getConsigneePhone()).isNull();
        assertThat(detail.getAddress()).isNull();
        assertThat(detail.getOrderNumber()).isNotBlank();
        assertThat(detail.getItems()).hasSize(1);
    }

    // ==================== 辅助方法 ====================

    private OrderSubmitRequest submitRequest() {
        OrderSubmitRequest request = new OrderSubmitRequest();
        request.setAddressId(addressId);
        request.setPetId(petId);
        request.setBeauticianId(beauticianId);
        request.setAppointmentDate(DATE);
        request.setTimeSlot(SLOT_10_12);
        request.setItems(List.of(item(onSaleServiceId, 2)));
        return request;
    }

    private OrderItemRequest item(Long serviceId, Integer quantity) {
        OrderItemRequest item = new OrderItemRequest();
        item.setServiceId(serviceId);
        item.setQuantity(quantity);
        return item;
    }

    private Long submit() {
        return submit(DATE);
    }

    private Long submit(LocalDate date) {
        OrderSubmitRequest request = submitRequest();
        request.setAppointmentDate(date);
        if (OTHER_DATE.equals(date)) {
            insertSchedule(beauticianId, OTHER_DATE, SLOT_10_12, 1, null);
        }
        return orderService.submit(request).getOrderId();
    }

    private OrderPayRequest payRequest(Long orderId) {
        OrderPayRequest request = new OrderPayRequest();
        request.setOrderId(orderId);
        return request;
    }

    private OrderCommentRequest commentRequest(Long orderId, Integer score, String content) {
        OrderCommentRequest request = new OrderCommentRequest();
        request.setOrderId(orderId);
        request.setScore(score);
        request.setContent(content);
        return request;
    }

    private BusinessException catchBusinessException(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
        Throwable throwable = catchThrowable(callable);
        assertThat(throwable).isInstanceOf(BusinessException.class);
        return (BusinessException) throwable;
    }

    private Long queryScheduleId(Long beauticianId, LocalDate date, String timeSlot) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM beautician_schedule WHERE beautician_id = ? AND date = ? AND time_slot = ?",
                Long.class, beauticianId, date, timeSlot);
    }

    private Long queryOrderId(Long scheduleId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT order_id FROM beautician_schedule WHERE id = ?", scheduleId);
        return row.get("order_id") == null ? null : ((Number) row.get("order_id")).longValue();
    }

    private Long queryCategoryId() {
        return jdbcTemplate.queryForObject("SELECT id FROM service_category WHERE name = ?",
                Long.class, "IT-订单分类");
    }

    private Long insertUser(String openid) {
        jdbcTemplate.update("INSERT INTO `user` (openid, sex, create_time, update_time) VALUES (?, 0, NOW(), NOW())", openid);
        return jdbcTemplate.queryForObject("SELECT id FROM `user` WHERE openid = ?", Long.class, openid);
    }

    private Long insertAddress(Long userId, String consignee) {
        jdbcTemplate.update("INSERT INTO address_book (user_id, consignee, phone, detail, is_default, create_time, update_time) "
                + "VALUES (?, ?, '13800000000', 'IT-详细地址', 1, NOW(), NOW())", userId, consignee);
        return jdbcTemplate.queryForObject("SELECT id FROM address_book WHERE user_id = ? AND consignee = ?",
                Long.class, userId, consignee);
    }

    private Long insertPet(Long userId, String name) {
        jdbcTemplate.update("INSERT INTO pet_profile (user_id, name, create_time, update_time) VALUES (?, ?, NOW(), NOW())",
                userId, name);
        return jdbcTemplate.queryForObject("SELECT id FROM pet_profile WHERE user_id = ? AND name = ?",
                Long.class, userId, name);
    }

    private Long insertBeautician(String name, int status) {
        jdbcTemplate.update("INSERT INTO beautician (name, score, status, create_time, update_time) "
                + "VALUES (?, 5.00, ?, NOW(), NOW())", name, status);
        return jdbcTemplate.queryForObject("SELECT id FROM beautician WHERE name = ?", Long.class, name);
    }

    private void insertSchedule(Long beauticianId, LocalDate date, String timeSlot, int available, Long orderId) {
        jdbcTemplate.update("INSERT INTO beautician_schedule (beautician_id, date, time_slot, available, order_id, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?, NOW(), NOW())", beauticianId, date, timeSlot, available, orderId);
    }

    private Long insertCategory(String name) {
        jdbcTemplate.update("INSERT INTO service_category (name, sort, status, create_time, update_time) "
                + "VALUES (?, 0, 1, NOW(), NOW())", name);
        return jdbcTemplate.queryForObject("SELECT id FROM service_category WHERE name = ?", Long.class, name);
    }

    private Long insertServiceItem(Long categoryId, String name, String price, int status) {
        jdbcTemplate.update("INSERT INTO service_item (category_id, name, price, status, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, NOW(), NOW())", categoryId, name, new BigDecimal(price), status);
        return jdbcTemplate.queryForObject("SELECT id FROM service_item WHERE category_id = ? AND name = ?",
                Long.class, categoryId, name);
    }

}
