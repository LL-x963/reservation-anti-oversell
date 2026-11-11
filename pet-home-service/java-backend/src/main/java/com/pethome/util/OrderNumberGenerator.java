package com.pethome.util;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 订单号生成工具类
 *
 * <p>格式：yyyyMMddHHmmssSSS + 6 位序号（如 20261010172530987000123）。</p>
 *
 * <p>生产路径使用 Redis 全局自增序号作为后缀：同一毫秒内的多笔订单序号不同，
 * 不存在随机数碰撞导致的下单失败。无参 {@link #generate()} 仅供测试与本地调试。</p>
 *
 * @author pet-home
 */
public final class OrderNumberGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RANDOM_BOUND = 10000;
    private static final long SEQUENCE_MODULO = 1_000_000L;

    private OrderNumberGenerator() {
    }

    /**
     * 按全局自增序号生成订单号（生产使用）
     *
     * @param sequence Redis 全局自增序号
     */
    public static String generate(long sequence) {
        return LocalDateTime.now().format(FORMATTER)
                + String.format("%06d", Math.floorMod(sequence, SEQUENCE_MODULO));
    }

    /**
     * 生成订单号（无 Redis 场景的兜底实现，仅测试 / 本地调试使用）
     */
    public static String generate() {
        return LocalDateTime.now().format(FORMATTER) + String.format("%04d", RANDOM.nextInt(RANDOM_BOUND));
    }

}
