package com.pethome.util;

import com.pethome.vo.UserProfileVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redis 工具类集成测试（需本地 Redis 服务）
 *
 * @author pet-home
 */
@SpringBootTest
class RedisUtilTest {

    private static final String KEY = "pethome:test:redis-util";
    private static final String LOCK_KEY = "pethome:test:redis-util:lock";

    @Autowired
    private RedisUtil redisUtil;

    @AfterEach
    void tearDown() {
        redisUtil.delete(KEY);
        redisUtil.delete(LOCK_KEY);
    }

    @Test
    void setAndGet() {
        redisUtil.set(KEY, "hello");
        assertThat(redisUtil.get(KEY)).isEqualTo("hello");
    }

    @Test
    void setWithExpire() {
        redisUtil.set(KEY, "hello", 60, TimeUnit.SECONDS);
        assertThat(redisUtil.get(KEY)).isEqualTo("hello");
        assertThat(redisUtil.getExpire(KEY)).isBetween(1L, 60L);
    }

    @Test
    void expireShouldUpdateTtl() {
        redisUtil.set(KEY, "hello");
        assertThat(redisUtil.getExpire(KEY)).isEqualTo(-1L);

        assertThat(redisUtil.expire(KEY, 30, TimeUnit.SECONDS)).isTrue();
        assertThat(redisUtil.getExpire(KEY)).isBetween(1L, 30L);
    }

    @Test
    void setIfAbsentShouldOnlySetOnce() {
        assertThat(redisUtil.setIfAbsent(LOCK_KEY, "first")).isTrue();
        assertThat(redisUtil.setIfAbsent(LOCK_KEY, "second")).isFalse();
        assertThat(redisUtil.get(LOCK_KEY)).isEqualTo("first");
    }

    @Test
    void setIfAbsentWithExpire() {
        assertThat(redisUtil.setIfAbsent(LOCK_KEY, "first", 60, TimeUnit.SECONDS)).isTrue();
        assertThat(redisUtil.getExpire(LOCK_KEY)).isBetween(1L, 60L);
    }

    @Test
    void hasKeyAndDelete() {
        redisUtil.set(KEY, "hello");
        assertThat(redisUtil.hasKey(KEY)).isTrue();

        assertThat(redisUtil.delete(KEY)).isTrue();
        assertThat(redisUtil.hasKey(KEY)).isFalse();
        assertThat(redisUtil.get(KEY)).isNull();
        assertThat(redisUtil.getExpire(KEY)).isEqualTo(-2L);
    }

    @Test
    void shouldStoreAndReadComplexObject() {
        UserProfileVO profile = new UserProfileVO();
        profile.setId(1L);
        profile.setName("测试用户");
        profile.setSex(1);

        redisUtil.set(KEY, profile);

        UserProfileVO cached = redisUtil.get(KEY, UserProfileVO.class);
        assertThat(cached).isNotNull();
        assertThat(cached.getId()).isEqualTo(1L);
        assertThat(cached.getName()).isEqualTo("测试用户");
        assertThat(cached.getSex()).isEqualTo(1);
    }

}
