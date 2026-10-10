package com.pethome.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
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

    private final RedisTemplate<String, Object> redisTemplate;

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

    // ==================== Hash 操作 ====================

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
