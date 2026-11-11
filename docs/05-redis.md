# 05 Redis 实践

## 1. 技术原理

### 1.1 何时该用 Redis

Redis 是内存 KV 存储，适合三类场景：

1. **热点临时数据**：读写频繁、丢了能重建（如预约车、验证码）；
2. **跨请求共享状态**：多实例共享（如登录态白名单）；
3. **原子计数与限流**：`INCR`/`SETNX` 天然原子。

**不适合**：需要事务与复杂查询的业务数据（用 MySQL）；需要持久可靠的消息（用 MQ）。

### 1.2 常用数据结构

| 结构 | 适用 |
|---|---|
| String | 单值缓存、计数器、token |
| Hash | 对象字段级读写（**预约车：field=服务id, value=数量**） |
| List | 队列/时间线 |
| Set / ZSet | 去重、排行榜、延迟队列 |

### 1.3 TTL 与过期

TTL 到了 key 自动删除，是「临时数据」的最好表达方式；**每次写操作刷新 TTL** 可以让数据在「持续活跃时不过期、长期不用自动清掉」。

## 2. 本项目怎么用

### 2.1 key 规范（`common/constant/RedisKeyConstant.java`）

统一业务前缀，便于排查与批量清理：

| 用途 | key 形态 | 结构 | TTL |
|---|---|---|---|
| 登录 token 白名单 | `pethome:login:token:{userType}:{userId}` | String | 与 token 有效期一致（24h） |
| 预约车 | `cart:{userId}` | Hash（field = serviceId, value = quantity） | 7 天，**每次操作刷新** |
| 催单限流 | `reminder:{orderId}:{userId}` | String | 秒级时间窗（见 `OrderServiceImpl.remind()`） |

```java
public static String loginTokenKey(UserType userType, Long userId) {
    return LOGIN_TOKEN_PREFIX + userType.getCode() + ":" + userId;   // pethome:login:token:1:5187
}
public static String cartKey(Long userId)  { return CART_PREFIX + userId; }        // cart:5187
public static String reminderKey(Long orderId, Long userId) { ... }                // reminder:3099:5187
```

### 2.2 登录态白名单（配合 JWT）

```java
// TokenService 签发时写入；AuthInterceptor 每次请求比对
redisUtil.set(RedisKeyConstant.loginTokenKey(userType, userId), token, expireSeconds, TimeUnit.SECONDS);
// 退出登录：直接删除 → token 立即失效
redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.USER, userId));
```

细节见 [03 章](03-auth-jwt.md)。

### 2.3 预约车：Hash + TTL（本项目最典型的 Redis 用法）

**为什么用 Redis 而不是表**：预约车是临时数据、读写频繁、不需要事务与历史；Hash 结构天然支持「同一服务重复加入 = 数量累加」。

```java
// service/impl/CartServiceImpl.java —— 加入预约车（累加 + 上限校验）
int target = currentQuantity(key, field) + request.getQuantity();
if (target > MAX_QUANTITY) throw new BusinessException("单个服务数量不能超过" + MAX_QUANTITY);
redisUtil.hSet(key, field, target);
refreshExpire(key);                       // 7 天 TTL，每次操作刷新

// 修改数量：直接覆盖（注意不是增量！前端传目标值）
redisUtil.hSet(key, field, request.getQuantity());
```

**列表查询时顺手清理失效数据**（服务已删除/已下架）：

```java
if (serviceId == null || quantity == null || item == null
        || !Integer.valueOf(STATUS_ON_SALE).equals(item.getStatus())) {
    invalidFields.add(entry.getKey());     // 收集脏 field
    continue;
}
...
if (!invalidFields.isEmpty()) {
    redisUtil.hDelete(key, invalidFields.toArray());   // 一次批量删除
    log.info("预约车自动清理失效服务: userId={}, fields={}", userId, invalidFields);
}
```

这是一个很实用的模式：**读路径顺便做自愈**，避免脏数据永远留在缓存里。

### 2.4 催单限流

以 `reminder:{orderId}:{userId}` 为 key 做**时间窗限流**：首次催单写入 key（带 TTL），窗口内再次催单直接拒绝，返回 `bizCode=603` + 「催单过于频繁，请稍后再试」（测试 `OrderApiTest#reminderShouldRateLimitWithBizCode603` 覆盖）。

key 里带上 `userId` 与 `orderId` 的组合，保证「不同用户互不影响、同一用户对不同订单也互不影响」。

### 2.5 其他 Redis 用途

- 下单成功后清空预约车：`cartService.clearCart(userId)`（见 [07 章](07-order-domain.md)）；
- 管理端排班、订单等业务数据**不入 Redis**，直接在 MySQL（避免缓存一致性复杂度）。

## 3. 易错点与实际踩过的坑

1. **预约车「修改数量」是覆盖不是增量**：`PUT /user/cart/update` 传的是**目标数量**（`CartUpdateRequest.quantity` 注释明确写「直接覆盖原有数量」），与 `add` 的累加语义不同。前端实现加减器时如果按「传 delta」处理，数量会指数级放大。
2. **数量上限要前后端一起拦**：后端 `checkQuantity` 限制 1~99；前端加减器也要在 1 与 99 处禁用按钮，否则用户会收到一串报错。
3. **TTL 不刷新会「莫名清空」**：只用 `hSet` 不调 `refreshExpire`，预约车会在首次写入 7 天后无声消失。本项目所有写操作后都调用了 `refreshExpire(key)`（内部判断 key 是否存在，避免刷新出空 key）。
4. **不要用 Redis 存需要强一致的业务数据**：本项目订单、排班全在 MySQL，Redis 只做「可重建」的数据；这样即使 Redis 重启也不会出现业务数据不一致。
5. **key 设计要能一眼看出归属**：统一 `pethome:` 前缀 + 语义化中间段，排查时 `KEYS pethome:login:token:*` 即可定位；反之若 key 是随机串，线上事故时无从下手。
6. **缓存与 DB 的一致性**：预约车展示的服务名/价格/图片**每次实时查 MySQL**（`listByIds`），Redis 里只存 `serviceId → quantity`。这样服务改价立刻生效，不会有「缓存价格过期」的问题——**能只缓存 key 就不缓存值**。
