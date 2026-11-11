# 02 后端分层与统一约定

## 1. 技术原理

### 1.1 分层为什么要分

分层（Controller → Service → Mapper）解决的是**职责与依赖方向**问题：

- **Controller**：只做「取参数 → 调 Service → 包返回」，不写业务判断；
- **Service**：业务规则、事务边界、跨表编排都在这里（本项目的核心复杂度都在这层）；
- **Mapper**：只与数据库打交道，复杂 SQL 用注解写在 Mapper 接口上。

依赖方向永远是 Controller → Service → Mapper，反过来意味着设计出了问题。

### 1.2 统一返回与全局异常

Web 接口的返回结构必须统一，否则前端要为每个接口写一套解析。本项目用 `Result`：

```java
// pet-home-service/java-backend/src/main/java/com/pethome/common/Result.java
public static final Integer SUCCESS_CODE = 1;   // 成功
public static final Integer FAIL_CODE = 0;      // 失败
private Integer code;      // 1 成功 / 0 失败
private Integer bizCode;   // 业务错误码，仅失败时返回（如 601 该时段不可约）
private String msg;
private Object data;
```

**为什么要 `bizCode`**：前端有时要按「具体原因」做不同处理（例如 603 = 催单过于频繁 → 提示稍后再试），而 `msg` 是给人看的、可能随时改文案。`code` 只表达成败，`bizCode` 表达「为什么失败」。

全局异常处理把各类异常收敛成同一个结构，避免把堆栈暴露给调用方：

```java
// common/exception/GlobalExceptionHandler.java
@ExceptionHandler(BusinessException.class)
public Result handleBusinessException(BusinessException e) {
    return Result.error(e.getCode(), e.getMessage());   // code=0，业务错误码走 bizCode
}

@ExceptionHandler(MethodArgumentNotValidException.class)
public Result handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
    String msg = e.getBindingResult().getFieldErrors().stream()
            .map(this::formatFieldError)                 // 字段名 + 中文提示
            .collect(Collectors.joining("; "));
    return Result.error(msg);
}
```

内部异常链（`@ExceptionHandler(Exception.class)`）统一返回「系统繁忙，请稍后重试」，堆栈只进日志。

### 1.3 参数校验：Bean Validation

在 DTO 上用注解声明规则，Controller 上加 `@Valid` 即生效，规则与文案都写在 DTO 里（这是本项目的统一习惯，也是「契约」的一部分）：

```java
// dto/OrderSubmitRequest.java
@NotNull(message = "地址id不能为空") @Min(value = 1, message = "地址id不合法")
private Long addressId;

@NotNull(message = "预约日期不能为空")
@FutureOrPresent(message = "预约日期不能早于今天")
@JsonFormat(pattern = "yyyy-MM-dd")
private LocalDate appointmentDate;

@NotEmpty(message = "服务项不能为空")
@Size(max = 20, message = "单次下单服务项不能超过20个")
private List<@Valid OrderItemRequest> items;

@Size(max = 200, message = "备注长度不能超过200")
private String remark;
```

嵌套集合用 `List<@Valid XxxRequest>` 才能让内层对象的注解生效（不然只会校验集合大小、不校验元素内部）。

### 1.4 ThreadLocal 上下文

鉴权通过后把 `userId + userType` 放进 ThreadLocal，Service 层随处可取，避免每个方法都传 `userId`：

```java
// common/context/UserContext.java（用法）
Long userId = UserContext.requireUserId();   // 取不到直接抛异常，比 null 传递更安全
```

**必须在拦截器 `afterCompletion` 里清理**（Tomcat 线程池会复用线程）：

```java
// interceptor/AuthInterceptor.java
@Override
public void afterCompletion(...) {
    UserContext.clear();   // 不清理会导致下一个请求读到上一个用户的身份 → 越权
}
```

## 2. 本项目怎么用

- 统一返回与异常：`common/Result.java`、`common/exception/GlobalExceptionHandler.java`、`common/exception/BusinessException.java`
- 常量与枚举：`common/constant/`（`RedisKeyConstant`、`MqConstant`、`ErrorCodeConstant`）、`common/enums/`（`OrderStatus`、`TimeSlot`、`UserType`、`StatisticsType` 等）
- 上下文：`common/context/UserContext.java` + `interceptor/AuthInterceptor.java`
- 实体基类：`entity/BaseEntity.java`（`createTime` / `updateTime` 由数据库 `DEFAULT CURRENT_TIMESTAMP` + `ON UPDATE` 维护，无需应用层填充）
- 上传与静态资源：`config/UploadProperties.java` + `WebMvcConfig.addResourceHandlers()`，把 `./upload` 映射为 `/upload/**`
- 跨域：`WebMvcConfig.addCorsMappings()`（开发环境用 Vite 代理时其实用不到，保留是为了直连调试）

典型 Controller 写法（一律只做转发）：

```java
// controller/UserOrderController.java
@PostMapping("/submit")
public Result submit(@RequestBody @Valid OrderSubmitRequest request) {
    return Result.success(orderService.submit(request));
}
```

## 3. 易错点与实际踩过的坑

1. **校验失败返回的是 HTTP 200 + `code=0`**，不是 400。这是全局异常处理器的统一策略，前端（以及测试）必须按 `code` 判断，不能只看 HTTP 状态码。反之**未登录返回的是真正的 HTTP 401**（见 [03 章](03-auth-jwt.md)）——**两套语义要分清楚**。
2. **Spring 的 `StringUtils` 没有 `isBlank`**：它只有 `hasText`。本项目在 `OrderServiceImpl` 里写备注判空时踩过（编译报「找不到符号 isBlank」），正确写法是 `StringUtils.hasText(x) ? x.trim() : null`。commons-lang3 的 `StringUtils` 才有 `isBlank`，两个包**不要混用**。
3. **Jackson 全局配置了 `default-property-inclusion: non_null`**（见 `application.yml`）：VO 里值为 `null` 的字段**不会出现在 JSON 里**。所以前端要用真值判断（`v-if="detail.comment"`）而不是判断 key 是否存在；写接口测试时也不要断言 `containsKey("xxx")` 为 true 除非该字段一定有值。
4. **ThreadLocal 忘记清理 = 越权**：拦截器必须实现 `afterCompletion` 并 `UserContext.clear()`。
5. **`@ExceptionHandler(DuplicateKeyException.class)` 是兜底而非主逻辑**：唯一索引冲突（如宠物重名、手机号重复注册）正常应由 Service 先查再拦，数据库异常兜底只保证「不把原始 SQL 错误抛给前端」。
6. **不要在图省事的地方用 `Result.success(entity)` 直接返回实体**：实体可能含密码等敏感字段。本项目所有对外返回都是 VO，`UserProfileVO` 不含 `password`（有测试断言这一点）。
