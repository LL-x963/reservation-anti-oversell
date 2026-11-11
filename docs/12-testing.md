# 12 测试与质量

## 1. 技术原理

### 1.1 测试金字塔

| 层 | 速度 | 覆盖对象 | 本项目对应 |
|---|---|---|---|
| 单元测试 | 毫秒级 | 纯逻辑（工具类、枚举、异常处理） | `util/*Test`、`common/*Test` |
| 集成测试 | 秒级 | Service + 真实依赖（数据库/Redis） | `service/*Test`、`persistence/*Test` |
| 接口测试 | 秒级 | 真实 HTTP + 真实依赖 + 鉴权链路 | `web/*ApiTest` |
| 端到端 | 分钟级 | 浏览器/多端联调 | 人工探针（本项目无自动化 UI 测试） |

**原则**：能用便宜的层验证就不要用贵的层；但**契约类的东西必须在"接口层"验证**（因为契约是跨进程的）。

### 1.2 测试替身

- **Mock/Fake**：隔离外部依赖（本项目把 RAG 索引客户端替换成替身，避免测试依赖 Python 服务）；
- **真实依赖优先**：数据库、Redis 尽量用真实的（测试库），因为 SQL 语义、事务、唯一索引冲突这些"恰是易错点"，Mock 掉就没意义了。

### 1.3 造数与清理

集成/接口测试必须有「自造数据、自清理」能力，否则：

- 用例之间互相干扰（顺序依赖）；
- 污染业务数据（例如订单会进入统计、影响营业额）。

## 2. 本项目怎么用

### 2.1 规模与分布

后端共 **385 个用例**（`mvn clean test` 全绿），分布：

```
com/pethome/
├─ util/                  JwtUtilTest、RedisUtilTest
├─ common/                ResultTest、exception/GlobalExceptionHandlerTest（MockMvc standalone）
├─ config/                RabbitMQConfigTest（拓扑声明断言）
├─ persistence/           PersistenceIntegrationTest（实体/表结构一致性）
├─ service/               OrderServiceTest、CartServiceTest、BeauticianServiceTest、
│                         BeauticianScheduleServiceTest、KnowledgeDocServiceTest、
│                         KnowledgeIndexRebuildTest、KnowledgeServiceTest、PetProfileServiceTest、
│                         ServiceCategoryServiceTest、ServiceItemServiceTest、StatServiceTest
└─ web/                   AuthFlowIntegrationTest、AdminSeedLoginTest、UserLoginApiTest、
                          UserAddressApiTest、UserBeauticianApiTest、OrderApiTest、CartApiTest、
                          ServiceItemApiTest、ServiceCategoryApiTest、PetProfileApiTest、
                          StatApiTest、KnowledgeApiTest、KnowledgeDocApiTest、
                          BeauticianApiTest、BeauticianScheduleApiTest、FileUploadApiTest
```

### 2.2 接口测试的标准写法（本项目主力形态）

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)   // 随机端口，避免与其他实例冲突
class UserLoginApiTest {

    @Autowired private TestRestTemplate restTemplate;     // 真实 HTTP 客户端
    @Autowired private UserMapper userMapper;             // 造数/校验
    @Autowired private RedisUtil redisUtil;               // 清理登录态

    @AfterEach
    void tearDown() {
        loggedUserIds.forEach(id -> redisUtil.delete(RedisKeyConstant.loginTokenKey(UserType.USER, id)));
        userMapper.delete(new LambdaQueryWrapper<User>().eq(User::getPhone, NEW_PHONE));
    }

    @Test
    void demoAccountShouldLoginByPhoneAndPassword() {
        JSONObject data = loginOk(Map.of("phone", DEMO_PHONE, "password", DEMO_PASSWORD));
        assertThat(data.getString("name")).isEqualTo("演示用户");
        // 顺带断言"不泄露敏感字段"
        assertThat(profile(data.getString("token")).getJSONObject("data").containsKey("password")).isFalse();
    }
}
```

要点：

- 用 `Map.of(...)` 或原始 JSON 字符串构造请求体，**断言真实 JSON 字段名**（而不是 DTO 的 getter 名）；
- 断言里带上安全/契约意图（如「响应不含 password」「用户端响应不含 status」）；
- 造数用 `JdbcTemplate` 或 Mapper，清理放 `@AfterEach`，**包括 Redis 中的 token**。

### 2.3 种子数据回归测试

`AdminSeedLoginTest`、`UserLoginApiTest` 专门验证「Flyway dev 种子脚本（`db/dev/R__seed_dev_data.sql`）里的初始密码能登录」：

```java
assertThat(password).as("admin 密码应为 BCrypt 密文").startsWith("$2");
assertThat(passwordEncoder.matches(ADMIN_PASSWORD, password)).isTrue();
```

**为什么值得单独测**：种子脚本里的密码是硬编码密文，一旦算法或脚本写错（历史上曾误用 MD5），整个系统都登不进去，而这个问题在单元测试里发现不了。

### 2.4 异常处理的轻量测试（MockMvc）

`GlobalExceptionHandlerTest` 用 `MockMvcBuilders.standaloneSetup(new TestController()).setControllerAdvice(...)`——**不启动 Spring 容器**，几毫秒即可验证「业务异常 → code=0 + bizCode」「校验失败 → 带字段名的中文提示」「系统异常不暴露堆栈」等规则。

### 2.5 质量守门流程（本项目实际执行）

```
后端：mvn clean test        # 385 个用例，全绿才算完成
前端：npm run build         # 构建通过（同时验证语法/引用）
      lint（IDE 诊断）      # 零报错
契约：真实探针打一次接口     # 见 11 章三步法
```

## 3. 易错点与实际踩过的坑

1. **MyBatis 一级缓存导致测试断言失败（真实踩过）**
   用例里用 `jdbcTemplate.update` 改了地址的省市区，紧接着 `adminOrderService.detailForAdmin()` 里的 `selectById` 仍返回旧值，断言失败。原因是一级缓存（同一 SqlSession 内相同查询不重复查库）。
   **修复**：改用 `addressBookMapper.updateById(...)`（走 MyBatis 清缓存），并在用例注释里写明原因，避免后人改回去。

2. **测试造的数据会污染业务口径**：订单类用例若不清理，会进入营业额/排行统计，导致「演示数据看起来不对」。所以 `OrderApiTest` 之类的用例都记录了 `createdOrderIds` 并统一清理。

3. **必须用 `mvn clean test` 而不是 `mvn test`**：Maven 增量编译可能漏检修改过的文件，导致「跑的是旧 class」。本项目实际表现是新接口报 405、以及一个真实的编译错误被掩盖了很久。

4. **`@SpringBootTest` 需要真实依赖**：MySQL/Redis/RabbitMQ 任一未启动都会让大量用例失败（不是代码问题）。排查顺序：先确认三个中间件在监听。

5. **接口测试不要断言"实现细节"**：例如断言 `msg` 的完整文案（「该时段已被预约」）会让文案调整就挂；建议用 `contains(...)` 断言关键片段，或断言 `bizCode`（更稳定）。

6. **敏感字段断言要常态化**：`password`、`idNumber`、`phone` 这类字段，在相关 VO 首次出现时就加一条「不包含」的断言，比事后审计便宜得多（本项目 `UserProfileVO`、`BeauticianSimpleVO` 都有）。

7. **前端没有自动化测试**：目前只有构建 + lint + 人工探针。若要补，建议从「请求封装 + 工具函数」这类纯逻辑开始（vitest 单测成本最低），UI 层再考虑 Playwright。见 [14 章](14-todo-and-optimization.md)。
