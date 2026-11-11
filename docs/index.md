# 宠享到家 · 项目知识点手册

> 用途：个人复习用的知识手册。每章统一按「**技术原理 → 本项目怎么用（代码 + 文件路径）→ 易错点与实际踩过的坑**」组织。
> 所有代码路径均相对仓库根目录（`宠享到家/`），行号可能随代码演进而偏移，定位请以类名/方法名为准。

## 一、项目一句话

**宠物上门服务平台**：用户在 H5 端浏览服务、加入预约车、选定地址/宠物/美容师/时段下单；管理端做服务、美容师、排班、订单、知识库与数据统计的运营；后端提供业务与鉴权；另有一个独立的 RAG 服务用本地大模型做「养宠百科」知识问答。

## 二、四个模块速览

| 模块 | 目录 | 技术栈 | 端口 | 职责 |
|---|---|---|---|---|
| 后端 | `pet-home-service/java-backend` | Spring Boot 3.5 + MyBatis-Plus + MySQL + Redis + RabbitMQ + JWT | 8080 | 业务主服务：C 端与 B 端接口、下单与订单状态机、排班并发、MQ 延迟任务、知识库文档管理 |
| 管理后台 | `pet-home-service/pet-home-admin` | Vue3 + Vite + Element Plus + Pinia + ECharts | 5173 | 8 个运营页面：首页看板、服务分类/项目、美容师、排班、订单、数据统计、知识库文档 |
| 用户端 H5 | `pet-home-service/pet-home-h5` | Vue3 + Vite + Element Plus（移动端适配） | 5174 | 9 个页面：登录、首页、服务列表/详情、预约车、提交预约、我的订单、个人中心、AI 问答 |
| RAG 服务 | `pet-home-service/rag-service` | FastAPI + LangChain 1.x + langchain-chroma + langchain-ollama + Ollama | 8000 | 文档切分与向量化、检索问答、参考来源、索引重建 |

## 三、章节清单

**第一部分 · 总览**
- [01 项目总览与整体架构](01-overview.md)：模块职责、数据流、目录导览、关键设计取舍一览

**第二部分 · 按技术栈拆知识点**
- [02 后端分层与统一约定](02-backend-layering.md)：分层、统一返回、全局异常、参数校验、ThreadLocal 上下文
- [03 鉴权与会话](03-auth-jwt.md)：JWT 双身份、拦截器、Redis 白名单、401 语义、前端 token 处理
- [04 数据层 MyBatis-Plus](04-mybatis-plus.md)：条件构造器、分页、批量查询防 N+1、一级缓存坑
- [05 Redis 实践](05-redis.md)：登录态白名单、预约车 Hash + TTL、催单限流
- [06 RabbitMQ 延迟消息](06-rabbitmq-delay.md)：超时取消、到点提醒、拓扑与 mandatory 取舍、幂等
- [07 订单域：状态机与并发](07-order-domain.md)：7 状态流转、排班 CAS 占用、并发抢时段、事务边界
- [08 RAG 知识库服务](08-rag-langchain.md)：LangChain 1.x 用法、切分与向量化、检索阈值、索引重建、降级链路
- [09 管理后台前端](09-admin-frontend.md)：Vue3 工程约定、路由守卫、Pinia、ECharts 按需引入、列表/表单模式
- [10 用户端 H5](10-h5-frontend.md)：移动端适配、无限滚动、抽屉弹层、请求封装 silent、内存会话
- [11 前后端联调与契约](11-integration.md)：Vite 代理、契约核对方法、跨域与静态资源、启动顺序
- [12 测试与质量](12-testing.md)：测试分层、真实 HTTP + 真实 DB 用例、造数与清理、踩坑复盘

**第三部分 · 落地与运维**
- [13 环境搭建与启动手册](13-setup-and-run.md)：依赖、端口、命令、启动顺序、常见问题
- [14 已知待办与可优化项](14-todo-and-optimization.md)：功能缺口、性能与工程优化清单

## 四、按复习目的的阅读路径

| 想复习什么 | 建议路径 |
|---|---|
| 后端一条线打通 | 02 → 03 → 04 → 05 → 06 → 07 |
| 想搞懂 AI 问答怎么做的 | 08 → 03（鉴权）→ 11（Java 调用 Python 的坑） |
| 前端工程能力 | 09 → 10 → 11 → 12 |
| 面试前突击 | 01（取舍一览）→ 03 → 07 → 08 → 12（坑复盘） |
| 把项目重新跑起来 | 13 → 14 |

## 五、知识点索引（技术 → 章节）

| 技术点 | 章节 |
|---|---|
| Spring 分层 / 统一返回 / 全局异常 / Bean Validation | 02 |
| ThreadLocal 上下文 / 拦截器执行时机 | 02、03 |
| JWT（HS256）、双身份鉴权、Redis 白名单吊销 | 03 |
| 前端 token 存储、401 处理与跳转、请求拦截器 | 03、10 |
| MyBatis-Plus：BaseMapper、LambdaQueryWrapper、分页、@Select/@Update | 04、07 |
| MyBatis 一级缓存、N+1 与批量查询 | 04 |
| Redis：String、Hash、TTL、限流 | 05 |
| RabbitMQ：延迟消息插件、Publisher Confirm、mandatory、幂等消费 | 06 |
| 状态机建模、CAS 乐观锁、并发抢资源、事务边界 | 07 |
| FastAPI + LangChain 1.x（langchain-core/chroma/ollama）、Chroma、Ollama、Prompt 组装、检索阈值 | 08 |
| Vue3 组合式 API、Vue Router 守卫、Pinia、ECharts 按需引入 | 09 |
| 移动端适配（viewport/安全区域/dvh）、IntersectionObserver 无限滚动、抽屉与弹窗 | 10 |
| Vite 代理与构建产物、跨域、静态资源映射 | 11 |
| 测试分层：@SpringBootTest + TestRestTemplate + JdbcTemplate 造数、MockMvc | 12 |
| 工程化踩坑：增量编译、Jackson non_null、StringUtils 差异 | 02、04、11、12 |

## 六、文档维护约定

1. 每章末尾若有「本章踩坑」，都是从真实调试过程里沉淀出来的，改动代码前建议先看一眼；
2. 涉及接口契约的章节（03、07、08、11）与后端 DTO/VO 强相关，若接口字段变更请同步这两处；
3. 本文档只描述**现状**，未完成项统一放在 [14 待办与优化](14-todo-and-optimization.md)，避免与「已实现」混淆。
