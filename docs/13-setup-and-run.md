# 13 环境搭建与启动手册

> 目标：在一台新机器上把整套项目跑起来。按本文章节顺序执行即可。

## 1. 依赖清单

| 依赖 | 版本/说明 | 必需 | 默认端口 |
|---|---|---|---|
| JDK | 17+（编译级别见 `java-backend/pom.xml`） | ✅ | — |
| Maven | 3.8+ | ✅ | — |
| Node.js | 18+（本机 npm 11.x） | ✅ | — |
| MySQL | 8.0（库名 `pet_home`，账号 root/123456） | ✅ | 3306 |
| Redis | 本地 Redis（`requirepass 123456`） | ✅ | 6379 |
| RabbitMQ | 3.9+，**建议装延迟消息插件** | ✅ | 5672 |
| Ollama | 需 pull `deepseek-r1:1.5b` 与 `bge-m3` | AI 功能需要 | 11434 |
| Python | 3.x（本项目用 conda 环境 `langchain1.2`） | AI 功能需要 | — |

## 2. 初始化数据库

```bash
# 只需创建空库，建表由 Flyway 在应用启动时自动执行
mysql -uroot -p123456 -e "CREATE DATABASE IF NOT EXISTS pet_home DEFAULT CHARACTER SET utf8mb4;"
```

- 建表脚本：`java-backend/src/main/resources/db/migration/V1__init_schema.sql`，**不含 `DROP TABLE`**，对已有数据的库是安全的；
- 本地种子数据：`db/dev/R__seed_dev_data.sql`，只在 `dev` profile 加载，包含管理端 `admin / 123456`、H5 演示账号 `13800138000 / 123456` 及其地址与宠物；
- **生产环境不会加载 dev 种子**，演示账号不会出现在生产库；
- 已有数据的库首次接入 Flyway 时会以版本 1 建基线（`baseline-on-migrate`），不会重复建表；
- 手机号登录**不再自动注册**，新用户需通过 H5 登录页的「注册」入口创建（密码 8~32 位且含字母和数字）。

## 3. 启动顺序

```bash
# ① 中间件（本机已装服务）
#   MySQL 3306 / Redis 6379 / RabbitMQ 5672 保持运行

# ② RabbitMQ 延迟插件（只需一次，之后 Broker 重启仍生效）
rabbitmq-plugins enable rabbitmq_delayed_message_exchange
#   若暂时装不上：把 application.yml 的 pethome.mq.delayed-enabled 改为 false，
#   服务仍可启动，只是「超时自动取消」与「到点提醒」不生效。

# ③ Ollama 模型（只需一次）
ollama pull deepseek-r1:1.5b
ollama pull bge-m3

# ④ RAG 服务（可选，不启动时 AI 问答降级提示，其余功能正常）
cd pet-home-service/rag-service
python main.py                       # 监听 8000，首次调用才加载模型

# ⑤ 后端
cd pet-home-service/java-backend
mvn spring-boot:run                  # 监听 8080

# ⑥ 管理后台
cd pet-home-service/pet-home-admin
npm install && npm run dev           # 5173，自动打开浏览器

# ⑦ 用户端 H5
cd pet-home-service/pet-home-h5
npm install && npm run dev           # 5174，自动打开浏览器
```

## 4. 关键配置项（`java-backend/src/main/resources/application.yml`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `server.port` | 8080 | 后端端口 |
| `spring.datasource.*` | `jdbc:mysql://localhost:3306/pet_home`，root/123456 | 数据库连接 |
| `spring.data.redis.*` | localhost:6379 db0，密码 123456 | Redis |
| `spring.rabbitmq.*` | localhost:5672 guest/guest | MQ |
| `spring.jackson.default-property-inclusion` | `non_null` | **null 字段不出现在 JSON 里** |
| `pethome.jwt.secret` | 内置默认值（生产必须用环境变量覆盖） | HS256，长度 ≥32 字节 |
| `pethome.jwt.expire-seconds` | 86400 | token 24 小时 |
| `pethome.mq.delayed-enabled` | true | 延迟插件开关 |
| `pethome.order.cancel-delay-millis` | 1800000 | 待支付超时 30 分钟 |
| `pethome.knowledge.base-url` / `api-key` | `http://localhost:8000` / `pethome-rag-key` | RAG 服务地址与鉴权头 `X-API-Key` |
| `pethome.upload.base-dir` / `url-prefix` | `./upload` / `/upload/` | 上传目录与访问前缀 |

RAG 侧配置见 `rag-service/app/config/settings.py`，**所有项都可用环境变量覆盖**（如 `RAG_API_KEY`、`LLM_MODEL`、`CHROMA_PERSIST_DIR`），也可复制 `.env.example` 为 `.env`。

## 5. 跑通验证清单（建议按顺序点一遍）

**管理端（5173）**
1. `admin / 123456` 登录 → 首页看板有数据；
2. 服务分类 / 服务项目：新增一个分类与服务（可上传图片）；
3. 美容师：新增一位在职美容师；
4. **排班：给该美容师排上「明天 + 若干时段」**（不排班则 H5 下单会报「该时段不可约」）；
5. 知识库：新增文档 → 点「重建索引」→ 轮询到「正常」；
6. 订单 / 数据统计：能看到 H5 下的单。

**用户端 H5（5174）**
1. `13800138000 / 123456` 登录（或点底部演示账号自动填入）；
2. 首页看分类与热门服务；进服务详情 → 加入预约车；
3. 预约车 → 勾选 → 去结算；
4. 提交预约页：选地址 / 宠物 / 美容师 / 日期 / 时段 → 提交 → 跳「我的订单」；
5. 我的订单：去支付 → 取消 / 催单 / 评价 / 删除（按状态）；
6. 右下角 AI 助手：提问「猫咪多久洗一次澡」→ 有答案 + 参考来源；若 RAG 未启动 → 提示「知识库服务暂时繁忙」；
7. 个人中心 → 退出登录 → 回到登录页。

## 6. 常见问题排查

| 现象 | 原因与处理 |
|---|---|
| 前端接口报 502/ECONNREFUSED | 后端没起或端口不是 8080；先 `curl http://localhost:8080/api/user/profile` 验证（返回 401 即为正常在跑） |
| 页面一直在登录页 | token 失效或未登录（后端返回 401）→ 清 `localStorage` 重新登录 |
| AI 回答总是「知识库服务暂时繁忙」 | ① rag-service(8000) 未启动；② `X-API-Key` 两端不一致（表现是 401）；③ 管理端没重建过索引（文档 `index_status` 不是 2） |
| 重建索引一直失败 | 看后端日志 4xx 详情；确认 rag-service 可用、Ollama 已 pull 向量模型（`bge-m3`） |
| 图片 404 | 后端的 `/upload` 静态映射未生效（确认 `pethome.upload.base-dir` 目录存在），或前端没配 `/upload` 代理 |
| H5 下单报「该时段不可约」 | 管理端没有为该美容师排「该日期 + 该时段」的班，或该排班已被占用/关闭 |
| 启动报 MQ 相关错误 | 延迟插件未装且 `delayed-enabled` 仍为 true；或 RabbitMQ 未启动 |
| 登录接口返回 401 | 用户端与管理端 token 混用（两端不通用），或 token 已过期/已退出 |
| 改了 Java 代码但行为没变 | Maven 增量编译漏检 → 用 `mvn clean test`（见 [12 章](12-testing.md) 踩坑 3） |
| `npm install` 报 EPERM（缓存目录无权写入） | 换缓存目录：`npm install --cache "$env:TEMP\npm-cache-pethome"`（本项目在这台机器上就是这样装成功的） |

## 7. 真机联调（H5）

Vite 已配 `host: '0.0.0.0'`，手机与电脑同一局域网时直接访问 `http://<电脑IP>:5174` 即可；注意后端也要能从该网段访问（默认本机 8080 由 Vite 代理，无需额外配置）。
