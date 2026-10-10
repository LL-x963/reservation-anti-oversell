package com.pethome.util;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 订单号生成工具类
 *
 * <p>格式：yyyyMMddHHmmssSSS + 4 位随机数（如 2026101017253098701234），
 * 毫秒级时间戳保证有序性，随机位降低并发碰撞概率，最终唯一性由数据库 uk_order_number 兜底。</p>
 *
 * @author pet-home
 */
public final class OrderNumberGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RANDOM_BOUND = 10000;

    private OrderNumberGenerator() {
    }

    /**
     * 生成订单号
     */
    public static String generate() {
        return LocalDateTime.now().format(FORMATTER) + String.format("%04d", RANDOM.nextInt(RANDOM_BOUND));
    }

}
