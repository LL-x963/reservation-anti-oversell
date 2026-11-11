package com.pethome.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 常用操作工具类
 *
 * @author pet-home
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    /**
     * HINCRBY + 上限校验 + 超限回滚，全部在一次 Lua 调用内原子完成。
     * 返回 -1 表示超限，此时增量已被回滚。
     */
    private static final RedisScript<Long> HASH_INCREMENT_WITH_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local v = redis.call('HINCRBY', KEYS[1], ARGV[1], ARGV[2]) "
                    + "if v > tonumber(ARGV[3]) then "
                    + "redis.call('HINCRBY', KEYS[1], ARGV[1], -ARGV[2]) "
                    + "return -1 end "
                    + "return v", Long.class);

    private final RedisTemplate<String, Object> redisTemplate;

    /** 纯字符串模板：用于限流、失败计数等需要原子自增的数值型 key */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 普通缓存写入
     */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 带过期时间的缓存写入
     *
     * @param timeout 过期时间
     * @param unit    时间单位
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 获取缓存
     */
    public Object get(String key) {
        return key == null ? null : redisTemplate.opsForValue().get(key);
    }

    /**
     * 获取缓存并转换为指定类型
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (!clazz.isInstance(value)) {
            throw new ClassCastException("缓存类型不匹配, key=" + key + ", expect=" + clazz.getName()
                    + ", actual=" + value.getClass().getName());
        }
        return (T) value;
    }

    /**
     * 删除缓存
     */
    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    /**
     * 批量删除缓存
     *
     * @return 实际删除的数量
     */
    public long delete(Collection<String> keys) {
        Long count = redisTemplate.delete(keys);
        return count == null ? 0L : count;
    }

    /**
     * 判断 key 是否存在
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 设置过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
    }

    /**
     * 获取剩余过期时间（秒），-1 表示永久，-2 表示 key 不存在
     */
    public long getExpire(String key) {
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return expire == null ? -2L : expire;
    }

    /**
     * 不存在时写入（分布式锁 / 幂等常用）
     *
     * @return true 表示写入成功（此前不存在）
     */
    public boolean setIfAbsent(String key, Object value) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value));
    }

    /**
     * 不存在时写入并设置过期时间
     *
     * @return true 表示写入成功（此前不存在）
     */
    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
    }

    /**
     * 原子自增（Redis INCRBY），用于限流与失败计数。
     *
     * <p>刻意使用 {@link StringRedisTemplate} 而非 JSON 序列化的模板：
     * 计数是纯数字，走 JSON 序列化会产生类型包装，读取时还要反解。</p>
     *
     * @return 自增后的值
     */
    public long increment(String key, long delta) {
        Long value = stringRedisTemplate.opsForValue().increment(key, delta);
        return value == null ? 0L : value;
    }

    /**
     * 以纯字符串方式读取（与 {@link #increment} 配套）
     */
    public String getString(String key) {
        return key == null ? null : stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 纯字符串写入（带过期时间），与 {@link #getString} 配套
     */
    public void setString(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /**
     * 不存在时以纯字符串写入并设置过期时间（下单幂等等场景使用）
     *
     * @return true 表示写入成功（此前不存在）
     */
    public boolean setIfAbsentString(String key, String value, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
    }

    // ==================== Hash 操作 ====================

    /**
     * Hash 字段原子自增（HINCRBY）。
     *
     * <p>用于预约车累加数量：读-改-写（HGET + HSET）在并发下会丢更新，
     * HINCRBY 由 Redis 单线程保证原子性。</p>
     *
     * @return 自增后的值
     */
    public long hIncrement(String key, String field, long delta) {
        Long value = stringRedisTemplate.opsForHash().increment(key, field, delta);
        return value == null ? 0L : value;
    }

    /**
     * Hash 字段原子自增并校验上限（Lua 脚本保证「累加 + 判断 + 回滚」原子执行）。
     *
     * @return 自增后的值；返回 -1 表示超过上限，且本次增量已被回滚
     */
    public long hIncrementWithLimit(String key, String field, long delta, long max) {
        Long result = stringRedisTemplate.execute(HASH_INCREMENT_WITH_LIMIT_SCRIPT,
                Collections.singletonList(key), field, String.valueOf(delta), String.valueOf(max));
        return result == null ? -1L : result;
    }

    /**
     * Hash 写入单个字段（纯字符串）
     */
    public void hSetString(String key, String field, String value) {
        stringRedisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * Hash 是否存在指定字段（纯字符串）
     */
    public boolean hHasKeyString(String key, String field) {
        return key != null && Boolean.TRUE.equals(stringRedisTemplate.opsForHash().hasKey(key, field));
    }

    /**
     * Hash 删除字段（纯字符串）
     */
    public long hDeleteString(String key, String... fields) {
        if (key == null || fields == null || fields.length == 0) {
            return 0L;
        }
        Long count = stringRedisTemplate.opsForHash().delete(key, (Object[]) fields);
        return count == null ? 0L : count;
    }

    /**
     * Hash 读取全部字段（纯字符串）
     */
    public Map<Object, Object> hGetAllString(String key) {
        if (key == null) {
            return Collections.emptyMap();
        }
        return stringRedisTemplate.opsForHash().entries(key);
    }

    /**
     * Hash 写入单个字段
     */
    public void hSet(String key, String field, Object value) {
        redisTemplate.opsForHash().put(key, field, value);
    }

    /**
     * Hash 批量写入
     */
    public void hSetAll(String key, Map<String, Object> map) {
        if (CollectionUtils.isEmpty(map)) {
            return;
        }
        redisTemplate.opsForHash().putAll(key, map);
    }

    /**
     * Hash 读取单个字段
     */
    public Object hGet(String key, String field) {
        return key == null ? null : redisTemplate.opsForHash().get(key, field);
    }

    /**
     * Hash 是否存在指定字段
     */
    public boolean hHasKey(String key, String field) {
        return key != null && Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, field));
    }

    /**
     * Hash 删除字段（hash 为空时 Redis 会自动删除该 key）
     *
     * @return 实际删除的字段数量
     */
    public long hDelete(String key, Object... fields) {
        if (key == null || fields == null || fields.length == 0) {
            return 0L;
        }
        Long count = redisTemplate.opsForHash().delete(key, fields);
        return count == null ? 0L : count;
    }

    /**
     * Hash 全部字段名
     */
    public Set<Object> hKeys(String key) {
        if (key == null) {
            return Collections.emptySet();
        }
        return redisTemplate.opsForHash().keys(key);
    }

    /**
     * Hash 字段数量
     */
    public long hSize(String key) {
        if (key == null) {
            return 0L;
        }
        Long size = redisTemplate.opsForHash().size(key);
        return size == null ? 0L : size;
    }

    /**
     * Hash 读取全部字段
     */
    public Map<Object, Object> hGetAll(String key) {
        if (key == null) {
            return Collections.emptyMap();
        }
        return redisTemplate.opsForHash().entries(key);
    }

}
