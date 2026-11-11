# 14 已知待办与可优化项

> 本文档只列**尚未实现 / 尚未验证**的项，避免与「已实现」混淆。每项都标明原因与建议做法。

## 1. 功能缺口

| # | 缺口 | 现状 | 建议做法 |
|---|---|---|---|
| 1 | 用户端**地址增删改** | 只有只读 `GET /api/user/address/list`（下单页选地址够用） | 补 `POST/PUT/DELETE /api/user/address/*` + H5 地址管理页；`address_book` 表字段已齐（含 `is_default`、`label`） |
| 2 | 管理端**改派美容师不换排班** | 状态 2/3 可改派，但只改 `order.beautician_id`，排班占用不变 | 若业务要求"改派即重新占资源"：释放旧排班（`releaseSlot`）+ 占用新排班（`occupySlot`），需处理两边事务与失败回滚 |
| 3 | **真实支付** | 现在是 `POST /api/user/order/pay` 模拟支付 | 接微信/支付宝支付：预下单、回调验签、幂等处理回调、对账 |
| 4 | 订单**评价**较简单 | 只有评分 + 文字 | 可加评价图片上传、追评、管理端回复 |
| 5 | **消息通知**未落地到用户可见 | MQ 到点提醒只写了 `reminder_status` | 接微信模板消息 / 短信 / App 推送 |
| 6 | H5 **未接问答历史** | 后端已存 `ai_conversation` 且提供分页/删除接口，H5 只用内存会话 | 加"历史问答"入口，调 `GET /api/user/knowledge/history/page` |
| 7 | RAG **流式输出** | 一次性返回整段答案，长答案要等 | FastAPI 侧改 SSE/流式，Java 侧转发（Spring 的 `SseEmitter`），前端逐字渲染 |
| 8 | 知识库**文档分类维护** | 分类是固定枚举（护理/饮食/疾病预防） | 若需运营自定义分类，改成表 + 管理页 |
| 9 | 管理端**用户/宠物管理** | 只有 C 端用户自己的宠物档案 | 后台视角的用户列表与宠物档案查询（注意隐私字段脱敏） |
| 10 | **优惠券/会员**体系 | 无 | 独立模块，涉及金额计算要谨慎（BigDecimal + 优惠分摊） |

## 2. 性能与体积

| # | 项 | 现状 | 建议 |
|---|---|---|---|
| 1 | 前端组件库**全量引入** | `main.js` 全量 Element Plus + 全部图标；两个前端主包分别约 1.18MB（gzip ≈380KB）+ CSS 354KB | 引入 `unplugin-vue-components` + `unplugin-auto-import` 做按需；图标改为按名导入需要的几十个 |
| 2 | ECharts 体积 | 已按需注册（Bar/Line + Grid/Tooltip + Canvas），Dashboard 与 Stat 共用同一 chunk | 若只首页用，可考虑懒加载图表组件 |
| 3 | 数据库索引 | 已有 `uk_openid`、`uk_phone`、`uk_category_name` 等 | 订单列表建议加 `service_order(user_id, status, id)` 复合索引；排班表 `(beautician_id, date, time_slot)` 唯一索引（防重复排班） |
| 4 | 热点查询缓存 | 服务列表/分类每次查库 | 分类、上架服务列表可加 Redis 短 TTL 缓存（改动频率低） |
| 5 | 列表 N+1 | 订单列表已批量装配（用户名/宠物名/美容师/明细） | 其他列表新增关联字段时沿用 `getXxxMap` 批量模式，不要循环查 |
| 6 | 上传文件 | 存本地 `./upload` 并由 Spring 映射为静态资源 | 生产建议换对象存储（OSS/MinIO），并加图片压缩 |

## 3. 工程与质量

| # | 项 | 说明 |
|---|---|---|
| 1 | **前端无自动化测试** | 目前只有 `npm run build` + lint + 人工探针；建议先补 vitest 覆盖 `utils/`（request 拦截器、format、auth）与组合式函数 |
| 2 | **无 CI** | 建议加流水线：后端 `mvn clean test` + 前端 `npm ci && npm run build`；注意 CI 需要 MySQL/Redis/RabbitMQ（可用 Testcontainers 改造） |
| 3 | **无容器化/部署脚本** | 建议补 Dockerfile（backend/frontends/rag）× docker-compose（含 MySQL/Redis/RabbitMQ/Ollama） |
| 4 | **生产部署方案未落地** | 规划：Nginx 托管两个 `dist/` + 反代 `/api`、`/upload` 到后端；RAG 服务只在内网暴露 |
| 5 | **运行时验证不足** | 前端页面做过构建与接口层验证，但**未在浏览器里完整点过一遍**（弹层动画、无限滚动触发时机、键盘弹出适配等属视觉项）；建议补一轮人工回归 + Playwright 冒烟 |
| 6 | **接口文档** | `WebMvcConfig` 已放行 `/doc.html`、`/swagger-ui/**` 等路径，但未接入完整 OpenAPI（如 springdoc） |
| 7 | **日志与可观测** | 只有控制台日志；建议接 Loki/ELK，关键链路（下单、MQ 消费、RAG 调用）打 traceId |
| 8 | `.idea/` 等 IDE 文件未忽略 | 仓库根目录可加 `.gitignore`（`pet-home-service/.idea/` 目前仍是未跟踪状态） |

## 4. 安全与稳定性

| # | 项 | 现状 | 建议 |
|---|---|---|---|
| 1 | token 存 `localStorage` | H5 与管理端都是 | 换 httpOnly + Secure Cookie 并加 CSRF 防护；至少加 CSP 与输入转义 |
| 2 | JWT 密钥有默认值 | `application.yml` 内置默认串 | 生产必须用 `PETHOME_JWT_SECRET` 覆盖，并定期轮换 |
| 3 | CORS 过宽 | `allowedOriginPatterns("*")` + `allowCredentials(true)` | 生产收紧为白名单域名 |
| 4 | 无接口限流 | 登录、问答等接口可被高频调用 | 网关或应用层加限流（如 Bucket4j + Redis），问答接口尤其需要（大模型耗时耗资源） |
| 5 | 密码策略弱 | 仅长度 6~20 | 加复杂度与错误次数锁定（避免撞库） |
| 6 | 无数据备份策略 | 无 | MySQL 定期备份 + Redis 可重建（本身只存可重建数据，风险低） |
| 7 | 演示账号/明文口令散落 | 演示账号写在登录页与 SQL | 正式上线前移除页面上的演示账号提示与种子账号 |

## 5. 建议的推进顺序（若要继续做）

1. **补地址增删改**（功能完整性缺口最直观，且表结构现成）；
2. **前端按需引入 + 浏览器回归**（体积与体验，收益立刻可见）；
3. **Docker 化 + CI**（可复现环境，后续加分项都依赖它）；
4. **RAG 流式输出**（体验提升明显）；
5. **真实支付与通知**（业务闭环的最后一块）。
