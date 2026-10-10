package com.pethome.service;

import com.pethome.common.constant.RedisKeyConstant;
import com.pethome.common.context.UserContext;
import com.pethome.common.enums.UserType;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.CartAddRequest;
import com.pethome.dto.CartUpdateRequest;
import com.pethome.util.RedisUtil;
import com.pethome.vo.CartItemVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 预约车服务层测试（Redis 存储，非事务，测试自行清理数据）
 *
 * @author pet-home
 */
@SpringBootTest
class CartServiceTest {

    /** 7 天 = 604800 秒 */
    private static final long SEVEN_DAYS_SECONDS = 7 * 24 * 60 * 60L;

    private static final String CATEGORY_NAME = "IT-购物车-分类";
    private static final String SERVICE_A = "IT-购物车-服务A";
    private static final String SERVICE_B = "IT-购物车-服务B";

    /** 合成用户 id，预约车不依赖数据库用户表 */
    private static final Long USER_A = 990001L;
    private static final Long USER_B = 990002L;

    @Autowired
    private CartService cartService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long serviceAId;
    private Long serviceBId;

    @BeforeEach
    void setUp() {
        cleanUp();

        Long categoryId = insertCategory(CATEGORY_NAME);
        serviceAId = insertServiceItem(categoryId, SERVICE_A, "99.00", "/upload/it-a.png", 1);
        serviceBId = insertServiceItem(categoryId, SERVICE_B, "199.50", "/upload/it-b.png", 1);

        UserContext.set(USER_A, UserType.USER);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        cleanUp();
    }

    // ==================== 添加 ====================

    @Test
    void addShouldStoreQuantityWithSevenDayTtl() {
        cartService.add(addRequest(serviceAId, 2));

        String key = RedisKeyConstant.cartKey(USER_A);
        assertThat(String.valueOf(redisUtil.hGet(key, String.valueOf(serviceAId)))).isEqualTo("2");
        assertThat(redisUtil.getExpire(key)).isBetween(SEVEN_DAYS_SECONDS - 120, SEVEN_DAYS_SECONDS);
    }

    @Test
    void addShouldAccumulateQuantity() {
        cartService.add(addRequest(serviceAId, 1));
        cartService.add(addRequest(serviceAId, 2));

        assertThat(redisUtil.hGet(RedisKeyConstant.cartKey(USER_A), String.valueOf(serviceAId)))
                .isEqualTo(3);
    }

    @Test
    void addShouldRejectExceedingMaxQuantity() {
        cartService.add(addRequest(serviceAId, 99));

        assertThatThrownBy(() -> cartService.add(addRequest(serviceAId, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("单个服务数量不能超过99");
    }

    @Test
    void addShouldRejectQuantityOutOfRange() {
        assertThatThrownBy(() -> cartService.add(addRequest(serviceAId, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量必须在");
        assertThatThrownBy(() -> cartService.add(addRequest(serviceAId, 100)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量必须在");
    }

    @Test
    void addShouldRejectNonexistentService() {
        assertThatThrownBy(() -> cartService.add(addRequest(99999999L, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务不存在");
    }

    @Test
    void addShouldRejectOfflineService() {
        Long offlineId = insertServiceItem(getCategoryId(), SERVICE_A + "下架", "50.00", null, 0);

        assertThatThrownBy(() -> cartService.add(addRequest(offlineId, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("服务已下架");
    }

    // ==================== 列表 ====================

    @Test
    void listShouldReturnServiceDetailsWithAmount() {
        cartService.add(addRequest(serviceAId, 2));
        cartService.add(addRequest(serviceBId, 1));

        List<CartItemVO> items = cartService.list();

        assertThat(items).hasSize(2);
        CartItemVO first = items.get(0);
        assertThat(first.getServiceId()).isEqualTo(serviceAId);
        assertThat(first.getName()).isEqualTo(SERVICE_A);
        assertThat(first.getPrice()).isEqualByComparingTo("99.00");
        assertThat(first.getImage()).isEqualTo("/upload/it-a.png");
        assertThat(first.getQuantity()).isEqualTo(2);
        assertThat(first.getAmount()).isEqualByComparingTo("198.00");

        CartItemVO second = items.get(1);
        assertThat(second.getServiceId()).isEqualTo(serviceBId);
        assertThat(second.getAmount()).isEqualByComparingTo("199.50");
    }

    @Test
    void listShouldReturnEmptyForEmptyCart() {
        assertThat(cartService.list()).isEmpty();
    }

    @Test
    void listShouldAutoCleanOfflineService() {
        cartService.add(addRequest(serviceAId, 1));
        cartService.add(addRequest(serviceBId, 1));
        jdbcTemplate.update("UPDATE service_item SET status = 0 WHERE id = ?", serviceAId);

        List<CartItemVO> items = cartService.list();

        assertThat(items).extracting(CartItemVO::getServiceId).containsExactly(serviceBId);
        // Redis 中失效字段已被清理
        assertThat(redisUtil.hHasKey(RedisKeyConstant.cartKey(USER_A), String.valueOf(serviceAId))).isFalse();
    }

    @Test
    void listShouldAutoCleanDeletedService() {
        cartService.add(addRequest(serviceAId, 1));
        jdbcTemplate.update("DELETE FROM service_item WHERE id = ?", serviceAId);

        assertThat(cartService.list()).isEmpty();
        assertThat(redisUtil.hSize(RedisKeyConstant.cartKey(USER_A))).isZero();
    }

    @Test
    void cartShouldBeIsolatedBetweenUsers() {
        cartService.add(addRequest(serviceAId, 1));

        UserContext.set(USER_B, UserType.USER);
        assertThat(cartService.list()).isEmpty();

        cartService.add(addRequest(serviceBId, 2));
        assertThat(cartService.list()).extracting(CartItemVO::getServiceId).containsExactly(serviceBId);

        UserContext.set(USER_A, UserType.USER);
        assertThat(cartService.list()).extracting(CartItemVO::getServiceId).containsExactly(serviceAId);
    }

    // ==================== 修改 ====================

    @Test
    void updateShouldOverwriteQuantity() {
        cartService.add(addRequest(serviceAId, 1));

        CartUpdateRequest request = new CartUpdateRequest();
        request.setServiceId(serviceAId);
        request.setQuantity(5);
        cartService.update(request);

        assertThat(redisUtil.hGet(RedisKeyConstant.cartKey(USER_A), String.valueOf(serviceAId)))
                .isEqualTo(5);
    }

    @Test
    void updateShouldRejectWhenServiceNotInCart() {
        CartUpdateRequest request = new CartUpdateRequest();
        request.setServiceId(serviceAId);
        request.setQuantity(5);

        assertThatThrownBy(() -> cartService.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预约车中不存在该服务");
    }

    @Test
    void updateShouldRejectQuantityOutOfRange() {
        cartService.add(addRequest(serviceAId, 1));

        CartUpdateRequest request = new CartUpdateRequest();
        request.setServiceId(serviceAId);
        request.setQuantity(100);

        assertThatThrownBy(() -> cartService.update(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量必须在");
    }

    // ==================== 删除与清空 ====================

    @Test
    void deleteShouldRemoveSingleService() {
        cartService.add(addRequest(serviceAId, 1));
        cartService.add(addRequest(serviceBId, 1));

        cartService.delete(serviceAId);

        assertThat(redisUtil.hHasKey(RedisKeyConstant.cartKey(USER_A), String.valueOf(serviceAId))).isFalse();
        assertThat(cartService.list()).extracting(CartItemVO::getServiceId).containsExactly(serviceBId);
    }

    @Test
    void deleteShouldRejectWhenServiceNotInCart() {
        assertThatThrownBy(() -> cartService.delete(serviceAId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("预约车中不存在该服务");
    }

    @Test
    void cleanShouldEmptyCart() {
        cartService.add(addRequest(serviceAId, 1));
        cartService.add(addRequest(serviceBId, 1));

        cartService.clean();

        assertThat(cartService.list()).isEmpty();
        assertThat(redisUtil.hasKey(RedisKeyConstant.cartKey(USER_A))).isFalse();
    }

    @Test
    void clearCartShouldClearByExplicitUserIdWithoutLoginContext() {
        cartService.add(addRequest(serviceAId, 3));

        // 模拟无请求上下文的调用（如订单模块 / MQ 消费）
        UserContext.clear();
        cartService.clearCart(USER_A);

        assertThat(redisUtil.hasKey(RedisKeyConstant.cartKey(USER_A))).isFalse();
        // 空 userId 为安全空操作
        cartService.clearCart(null);
    }

    // ==================== TTL 刷新 ====================

    @Test
    void everyOperationShouldRefreshTtl() {
        cartService.add(addRequest(serviceAId, 1));
        String key = RedisKeyConstant.cartKey(USER_A);

        // 人为把 TTL 缩短，验证后续操作会重置为 7 天
        redisUtil.expire(key, 60, TimeUnit.SECONDS);
        assertThat(redisUtil.getExpire(key)).isLessThanOrEqualTo(60L);

        cartService.add(addRequest(serviceBId, 1));
        assertThat(redisUtil.getExpire(key)).isBetween(SEVEN_DAYS_SECONDS - 120, SEVEN_DAYS_SECONDS);

        redisUtil.expire(key, 60, TimeUnit.SECONDS);
        cartService.list();
        assertThat(redisUtil.getExpire(key)).isBetween(SEVEN_DAYS_SECONDS - 120, SEVEN_DAYS_SECONDS);

        redisUtil.expire(key, 60, TimeUnit.SECONDS);
        CartUpdateRequest updateRequest = new CartUpdateRequest();
        updateRequest.setServiceId(serviceAId);
        updateRequest.setQuantity(3);
        cartService.update(updateRequest);
        assertThat(redisUtil.getExpire(key)).isBetween(SEVEN_DAYS_SECONDS - 120, SEVEN_DAYS_SECONDS);

        redisUtil.expire(key, 60, TimeUnit.SECONDS);
        cartService.delete(serviceBId);
        assertThat(redisUtil.getExpire(key)).isBetween(SEVEN_DAYS_SECONDS - 120, SEVEN_DAYS_SECONDS);
    }

    // ==================== 辅助方法 ====================

    private CartAddRequest addRequest(Long serviceId, Integer quantity) {
        CartAddRequest request = new CartAddRequest();
        request.setServiceId(serviceId);
        request.setQuantity(quantity);
        return request;
    }

    private Long getCategoryId() {
        return jdbcTemplate.queryForObject("SELECT id FROM service_category WHERE name = ?",
                Long.class, CATEGORY_NAME);
    }

    private Long insertCategory(String name) {
        jdbcTemplate.update("INSERT INTO service_category (name, sort, status, create_time, update_time) "
                + "VALUES (?, ?, ?, NOW(), NOW())", name, 0, 1);
        return jdbcTemplate.queryForObject("SELECT id FROM service_category WHERE name = ?", Long.class, name);
    }

    private Long insertServiceItem(Long categoryId, String name, String price, String image, int status) {
        jdbcTemplate.update("INSERT INTO service_item (category_id, name, price, image, status, create_time, update_time) "
                + "VALUES (?, ?, ?, ?, ?, NOW(), NOW())", categoryId, name, new BigDecimal(price), image, status);
        return jdbcTemplate.queryForObject("SELECT id FROM service_item WHERE category_id = ? AND name = ?",
                Long.class, categoryId, name);
    }

    private void cleanUp() {
        redisUtil.delete(RedisKeyConstant.cartKey(USER_A));
        redisUtil.delete(RedisKeyConstant.cartKey(USER_B));

        jdbcTemplate.update("DELETE FROM service_item WHERE category_id IN "
                + "(SELECT id FROM service_category WHERE name = ?)", CATEGORY_NAME);
        jdbcTemplate.update("DELETE FROM service_item WHERE name LIKE ?", "IT-购物车-%");
        jdbcTemplate.update("DELETE FROM service_category WHERE name = ?", CATEGORY_NAME);
    }

}
