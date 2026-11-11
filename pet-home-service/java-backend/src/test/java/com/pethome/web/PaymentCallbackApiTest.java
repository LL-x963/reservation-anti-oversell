package com.pethome.web;

import com.alibaba.fastjson2.JSON;
import com.pethome.payment.MockPaymentGateway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 支付回调接口集成测试（POST /api/payment/callback/{channel}）
 *
 * <p>覆盖：验签失败拒绝、正常回调置为待确认、重复回调幂等、金额不一致拒绝。
 * 回调接口免登录，安全性完全依赖验签，因此这里必须覆盖签名校验。</p>
 *
 * @author pet-home
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaymentCallbackApiTest {

    private static final String OPENID = "it_pay_callback_user";
    private static final String ORDER_NUMBER = "ITPAY20261111000001";
    private static final BigDecimal AMOUNT = new BigDecimal("99.00");

    private static final int STATUS_PENDING_PAY = 1;
    private static final int STATUS_PENDING_CONFIRM = 2;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockPaymentGateway mockPaymentGateway;

    private Long userId;
    private Long orderId;

    @BeforeEach
    void setUp() {
        cleanUp();
        jdbcTemplate.update("INSERT INTO `user` (`openid`, `name`, `sex`) VALUES (?, ?, 0)", OPENID, "支付回调测试用户");
        userId = jdbcTemplate.queryForObject("SELECT id FROM `user` WHERE openid = ?", Long.class, OPENID);

        jdbcTemplate.update("INSERT INTO `service_order` (`order_number`, `user_id`, `total_amount`, `status`, `reminder_status`) "
                + "VALUES (?, ?, ?, ?, 0)", ORDER_NUMBER, userId, AMOUNT, STATUS_PENDING_PAY);
        orderId = jdbcTemplate.queryForObject("SELECT id FROM `service_order` WHERE order_number = ?",
                Long.class, ORDER_NUMBER);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void callbackWithInvalidSignatureShouldBeRejected() {
        String body = callbackBody("TRADE-INVALID-1", AMOUNT);

        callback(body, "definitely-wrong-signature");

        assertThat(orderStatus()).as("验签失败不能改变订单状态").isEqualTo(STATUS_PENDING_PAY);
        assertThat(recordCount()).as("验签失败不能落支付流水").isZero();
    }

    @Test
    void validCallbackShouldMarkPaidAndBeIdempotent() {
        String body = callbackBody("TRADE-OK-1", AMOUNT);
        String signature = mockPaymentGateway.sign(body);

        ResponseEntity<String> first = callback(body, signature);
        assertThat(first.getBody()).isEqualTo("success");
        assertThat(orderStatus()).isEqualTo(STATUS_PENDING_CONFIRM);
        assertThat(recordCount()).isEqualTo(1L);

        // 渠道重复回调（同一 tradeNo）：必须幂等，不重复落流水、不改坏状态
        ResponseEntity<String> second = callback(body, signature);
        assertThat(second.getBody()).isEqualTo("success");
        assertThat(orderStatus()).isEqualTo(STATUS_PENDING_CONFIRM);
        assertThat(recordCount()).isEqualTo(1L);
    }

    @Test
    void callbackWithMismatchedAmountShouldBeRejected() {
        String body = callbackBody("TRADE-AMOUNT-1", new BigDecimal("0.01"));
        String signature = mockPaymentGateway.sign(body);

        callback(body, signature);

        assertThat(orderStatus()).as("金额不一致不能置为已支付").isEqualTo(STATUS_PENDING_PAY);
        assertThat(recordCount()).isZero();
    }

    private ResponseEntity<String> callback(String body, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(MockPaymentGateway.SIGN_HEADER, signature);
        return restTemplate.exchange("/api/payment/callback/mock", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
    }

    private String callbackBody(String tradeNo, BigDecimal amount) {
        return JSON.toJSONString(Map.of(
                "orderNumber", ORDER_NUMBER,
                "tradeNo", tradeNo,
                "amount", amount.toPlainString(),
                "success", true));
    }

    private Integer orderStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM `service_order` WHERE id = ?",
                Integer.class, orderId);
    }

    private long recordCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment_record WHERE order_number = ?",
                Long.class, ORDER_NUMBER);
        return count == null ? 0L : count;
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM payment_record WHERE order_number = ?", ORDER_NUMBER);
        jdbcTemplate.update("DELETE FROM `service_order` WHERE order_number = ?", ORDER_NUMBER);
        jdbcTemplate.update("DELETE FROM `user` WHERE openid = ?", OPENID);
    }

}
