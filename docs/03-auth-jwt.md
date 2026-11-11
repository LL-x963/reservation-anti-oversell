# 03 鉴权与会话

## 1. 技术原理

### 1.1 JWT 是什么、为什么用它

JWT（JSON Web Token）是一段自包含、带签名的字符串：`header.payload.signature`。服务端**不需要存会话**，只验签名即可解出 `userId`、`userType`、`exp`。

- 优点：无状态、易横向扩展、跨端（H5/小程序/管理端）通用；
- 缺点：**签发后无法主动作废**，且载荷只做 Base64 编码（不能放敏感信息）。

### 1.2 为什么还要 Redis 白名单

要支持「退出登录立即失效」，就得让 token 变成可作废的。本项目的折中方案：

- token 仍然无状态（验签即可解析身份，不查库）；
- 但登录时把 token 写入 Redis：`pethome:login:token:{userType}:{userId}`；
- 每次请求除了验签，还要**比对 Redis 里的 token 是否与请求携带的一致**；不一致 → 视为失效。

这样「退出登录」= 删掉 Redis key，token 立刻作废；同时保留了无状态校验的性能优势。**代价是每次请求多一次 Redis 读**（可接受）。

### 1.3 双身份（C 端用户 / 管理端员工）

同一套拦截器类通过构造参数区分身份，注册到两组路径上，避免写两遍：

```java
// config/WebMvcConfig.java
registry.addInterceptor(new AuthInterceptor(jwtUtil, redisUtil, jwtProperties, UserType.USER))
        .addPathPatterns("/api/user/**").excludePathPatterns(EXCLUDE_PATHS).order(1);

registry.addInterceptor(new AuthInterceptor(jwtUtil, redisUtil, jwtProperties, UserType.EMPLOYEE))
        .addPathPatterns("/api/admin/**").excludePathPatterns(EXCLUDE_PATHS).order(2);
```

于是**用户 token 访问管理端接口会因身份不匹配被拒**（有测试专门覆盖这种越权）。

## 2. 本项目怎么用

### 2.1 配置

```yaml
# application.yml → pethome.jwt
secret: ${PETHOME_JWT_SECRET:pethome-jwt-secret-key-please-change-me-in-production-2024}
expire-seconds: 86400          # 24 小时
header: Authorization
token-prefix: "Bearer "
```

密钥支持环境变量覆盖（生产必须覆盖，HS256 要求 ≥ 32 字节）。

### 2.2 校验链路

```java
// interceptor/AuthInterceptor.java（核心顺序不能变）
String token = jwtUtil.resolveToken(request.getHeader(jwtProperties.getHeader()));  // 去 Bearer 前缀
if (StringUtils.isBlank(token))  return unauthorized(response, "未登录，请先登录");
if (!jwtUtil.validateToken(token)) return unauthorized(response, "token 无效或已过期，请重新登录");
if (jwtUtil.parseUserType(token) != userType) return unauthorized(response, "token 身份与接口不匹配");
Long userId = jwtUtil.parseUserId(token);
Object cached = redisUtil.get(RedisKeyConstant.loginTokenKey(userType, userId));
if (cached == null || !token.equals(String.valueOf(cached))) {
    return unauthorized(response, "登录状态已失效，请重新登录");
}
UserContext.set(userId, userType);   // 后续 Service 通过 UserContext 取用户
```

未登录的响应是**真正的 HTTP 401** + `Result.error(msg)`：

```java
response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);   // 401
response.getWriter().write(JSON.toJSONString(Result.error(msg)));
```

### 2.3 管理端

- 登录：`POST /api/admin/login`（别名 `/api/admin/employee/login`），`EmployeeLoginRequest{username, password}`，密码用 **BCrypt** 校验；
- 用户不存在与密码错误返回同一句「用户名或密码错误」，避免暴露账号是否存在；
- 禁用员工（`status != 1`）禁止登录。

### 2.4 用户端（H5 用手机号 + 密码，小程序侧用 openid）

同一个接口承载两种模式，**二选一**：

```java
// service/impl/UserServiceImpl.java
public LoginVO login(UserLoginRequest request) {
    boolean hasOpenid = StringUtils.isNotBlank(request.getOpenid());
    boolean hasPhone  = StringUtils.isNotBlank(request.getPhone());
    if (hasOpenid == hasPhone) {   // 同时传或都不传都非法
        throw new BusinessException(hasOpenid ? "openid 与手机号密码登录只能二选一" : "请传入 openid 或 手机号+密码");
    }
    return hasOpenid ? loginByOpenid(request.getOpenid().trim())
                     : loginByPhone(request.getPhone().trim(), request.getPassword());
}
```

- 手机号不存在 → **自动注册**（密码 BCrypt 存 `user.password`，`openid` 留空）；
- 密码错误 → 统一提示「手机号或密码错误」；
- 并发注册触发 `uk_phone` 唯一索引冲突时，回查已有账号再校验密码（`catch DuplicateKeyException`）。

### 2.5 前端侧

| 端 | 存储 | 401 处理 |
|---|---|---|
| 管理后台 `pet-home-admin/src/utils/request.js` | `token` | 清登录态 + 提示 + 跳 `/login` |
| 用户端 H5 `pet-home-h5/src/utils/request.js` | `user_token` / `user_name` | 同上，并做了**并发去重**（多个请求同时 401 时只提示一次、只跳一次） |

H5 的适配（`utils/auth.js` + `utils/request.js`）：

```js
// 请求拦截：自动带 Bearer
config.headers.Authorization = `Bearer ${getToken()}`

// 响应拦截：兼容「响应体 code=401」与「HTTP 401」两种写法
if (res.code === CODE_UNAUTHORIZED || status === CODE_UNAUTHORIZED) handleUnauthorized(message)
```

`handleUnauthorized` 内部：`clearAuth()` → 提示 → `redirectToLogin()`（改 `location.hash` 带上 `redirect`，路由守卫据此回跳原页面）。

## 3. 易错点与实际踩过的坑

1. **未登录是 HTTP 401，不是业务码**：需求里常写成「code=401」，但后端实现是 `response.setStatus(401)` + 响应体 `{code:0,msg:"未登录，请先登录"}`。前端必须**同时兼容**，否则会「接口报错但没跳登录页」。
2. **token 前缀别漏空格**：`token-prefix: "Bearer "`，`resolveToken()` 会去掉前缀并 `trim()`，所以前端写成 `Bearer ${token}`；写成 `Bearer${token}` 也能兜住（会 trim），但带上空格更规范。
3. **改密码 / 改身份后一定要处理白名单**：本项目 `logout` 只删 key；若要支持「改密码踢下线」，需在改密码时同样删除/覆盖白名单 key。
4. **localStorage 存 token 有 XSS 风险**：这是为开发便捷做的取舍，见 [10 章](10-h5-frontend.md) 的安全提示；若要更安全需改用 httpOnly Cookie + CSRF 防护。
5. **不要把 `userType` 校验去掉**：去掉后 C 端 token 就能访问管理端接口（本项目有专门的测试用例防止回归）。
6. **管理端种子账号的密码必须是 BCrypt 密文**：Flyway dev 种子脚本（`db/dev/R__seed_dev_data.sql`）里 `admin` 的 password 是 `$2a$10$...`；历史上曾误用 MD5 导致登录必失败，现在有回归测试 `AdminSeedLoginTest` 守着。用户端演示账号同理（`13800138000 / 123456`）。

## 4. 上线加固后的认证行为

| 场景 | 改造前 | 改造后 |
|---|---|---|
| 微信登录 | 客户端直传 `openid` 即签发 token，可伪造任意用户 | 只接受一次性 `code`，服务端调 `code2session` 换 openid；未配置 appid/secret 时入口直接禁用 |
| openid 直登 | 默认开启 | 仅 `pethome.auth.allow-openid-direct-login=true`（本地/测试）可用；prod profile 固定 false，启动自检会拒绝开启 |
| 手机号登录 | 不存在即自动注册 | 必须已注册，未注册统一返回「手机号或密码错误」；注册走 `POST /api/user/register` |
| 密码强度 | 6~20 位任意字符 | 注册要求 8~32 位且同时包含字母和数字，拒绝常见弱口令 |
| 暴力破解 | 无限制 | 连续失败 5 次锁定 15 分钟（Redis 计数，按手机号 / 用户名） |
| 接口刷取 | 无限制 | 登录 / 注册按 IP 限流，超限返回 HTTP 429 |
| 管理端权限 | 员工登录后即可执行所有操作 | 员工分 ADMIN / OPERATOR，角色写入 JWT，`@RequireRole` 校验；取消订单、改派美容师仅 ADMIN |
