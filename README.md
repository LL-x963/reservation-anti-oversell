# 宠享到家（pet-home）

> 宠物上门服务平台：用户端 H5 + 管理后台 + Spring Boot 后端 + 本地 RAG 知识库问答服务。

宠享到家是一个可直接跑起来的全栈示例项目：用户在 H5 端浏览服务、加购、下单、支付、管理宠物档案与地址；
管理员在后台上架服务、排班、处理订单、维护知识库文档；AI 助手基于本地知识库回答宠物健康科普问题，
答案附带可溯源的参考资料，并对越界提问做确定性拦截。

## 目录

- [架构](#架构)
- [技术栈](#技术栈)
- [功能清单](#功能清单)
- [环境准备](#环境准备)
- [快速开始](#快速开始)
- [环境变量说明](#环境变量说明)
- [接口调用示例](#接口调用示例)
- [目录结构](#目录结构)

## 架构

```mermaid
flowchart LR
    subgraph Client["客户端"]
        H5["用户端 H5<br/>Vue3 + Vite :5174"]
        ADMIN["管理后台<br/>Vue3 + Element Plus + ECharts :5173"]
    end

    subgraph Backend["业务后端"]
        JAVA["java-backend<br/>Spring Boot 3.5 :8080"]
    end

    subgraph Infra["基础设施"]
        MYSQL[("MySQL 8<br/>pet_home")]
        REDIS[("Redis<br/>登录态 / 限流 / 缓存")]
        MQ[("RabbitMQ<br/>订单延迟取消 / 死信")]
        FS[("本地磁盘<br/>/upload")]
    end

    subgraph RAG["AI 知识库"]
        PY["rag-service<br/>FastAPI :8000"]
        OLLAMA["Ollama<br/>bge-m3 向量 + deepseek-r1 对话"]
        CHROMA[("Chroma<br/>向量索引")]
    end

    H5 -- "HTTP /api" --> JAVA
    ADMIN -- "HTTP /api" --> JAVA
    JAVA --> MYSQL
    JAVA --> REDIS
    JAVA --> MQ
    JAVA --> FS
    JAVA -- "X-API-Key /ask /reindex" --> PY
    PY --> OLLAMA
    PY --> CHROMA
```

请求链路要点：

1. H5 / 管理后台通过 Vite 代理把 `/api` 转发到 `java-backend`（开发期免跨域配置）。
2. 登录态使用 JWT + Redis 白名单，`/api/user/**` 与 `/api/admin/**` 分别校验 C 端与 B 端身份。
3. 下单写入订单表后投递 RabbitMQ 延迟消息，超时未支付自动取消；消费失败进入死信队列。
4. AI 问答由后端携带 `enabledDocIds`（当前启用文档范围）调用 `rag-service`，检索 + 生成均在本地完成。

## 技术栈

| 模块 | 技术选型 |
| --- | --- |
| 后端 | Java 17、Spring Boot 3.5.7、MyBatis-Plus 3.5.9、Flyway、JWT（jjwt 0.12.6）、Spring AMQP、Actuator |
| 存储 | MySQL 8、Redis、RabbitMQ（延迟队列 + 死信） |
| 用户端 | Vue 3.5、Vite 6、Element Plus、vue-router、axios |
| 管理端 | Vue 3.5、Vite 6、Element Plus、ECharts、Pinia |
| AI | Python 3.11~3.13、FastAPI、LangChain 1.x、Chroma 1.5、Ollama（bge-m3 / deepseek-r1:7b） |
| 测试 | JUnit 5 + AssertJ（后端 396 个用例）、Python `unittest` 冒烟测试 |

## 功能清单

**用户端（H5）**

- 手机号 + 密码注册 / 登录，登录态过期自动重登
- 服务分类、服务项目浏览与详情
- 购物车加购 / 改数量 / 删除 / 清空
- 下单、模拟支付、订单列表与详情、取消订单
- 宠物档案管理（品种、年龄、体重等）
- 地址簿管理（新增 / 修改 / 删除 / 切换默认地址）
- 可预约美容师与排班查询
- AI 宠物知识库问答（流式呈现答案与参考来源）

**管理端（Admin）**

- 管理员登录、鉴权拦截
- 服务分类与服务项目管理
- 美容师档案与排班管理
- 订单查询、状态流转
- 数据统计看板（营业额、订单量、服务热度）
- 知识库文档管理（增删改查、启用/停用、一键重建向量索引）
- 图片上传（魔数校验 + 类型/大小白名单）

**RAG 知识库服务**

- 文档切分、分批入库到 Chroma（可全量重建，重建互斥）
- 按 `enabledDocIds` 过滤后检索 top_k 片段，支持相关性阈值拦截
- 多轮对话上下文（最近 N 轮）、宠物信息注入
- 答案固定拼接免责声明，返回可溯源参考来源
- 引擎不可用（Ollama 未启动 / 模型未 pull）统一返回 503，不泄露内部堆栈

## 环境准备

| 依赖 | 版本要求 | 说明 |
| --- | --- | :-- |
| JDK | 17+ | 后端编译运行 |
| Maven | 3.9+ | 后端构建 |
| Node.js | 18+ | 两个前端工程 |
| Python | 3.11 ~ 3.13 | RAG 服务 |
| MySQL | 8.x | 库名 `pet_home`，Flyway 自动建表 |
| Redis | 6+ | 登录态、限流 |
| RabbitMQ | 3.9+ | 订单延迟取消 |
| Ollama | 最新 | 需 `ollama pull bge-m3` 与 `ollama pull deepseek-r1:7b` |

> AI 功能是可选能力：不启动 `rag-service` 时其余业务功能不受影响，仅知识库问答接口不可用。

## 快速开始

### 1. 准备数据库与中间件

```sql
CREATE DATABASE pet_home DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

启动 Redis、RabbitMQ（本地默认端口 6379 / 5672）。表结构与演示数据由 Flyway 在应用启动时自动执行
（`db/migration` 为正式脚本，`db/dev` 仅在 `dev` profile 下加载）。

### 2. 启动 RAG 服务

```bash
cd pet-home-service/rag-service
cp .env.example .env          # Windows: copy .env.example .env
pip install -r requirements.txt
ollama pull bge-m3
ollama pull deepseek-r1:1.5b
python main.py                # 默认 http://localhost:8000
```

### 3. 启动后端

```bash
cd pet-home-service/java-backend
mvn spring-boot:run           # 默认 http://localhost:8080，dev profile 自动启用
```

健康检查：`curl http://localhost:8080/actuator/health`，返回 `{"status":"UP"}` 即启动成功。

### 4. 启动前端

```bash
# 用户端 H5
cd pet-home-service/pet-home-h5
npm install && npm run dev    # http://localhost:5174

# 管理后台
cd pet-home-service/pet-home-admin
npm install && npm run dev    # http://localhost:5173
```

### 5. 初始化知识库

首次启动后向量库为空，可通过管理后台「知识库文档 → 重建索引」触发一次全量重建，
或直接调用 `POST /api/knowledge/reindex`（见下方示例）。

## 环境变量说明

### rag-service（读取 `.env` 或系统环境变量）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8000` | 服务监听端口 |
| `LOG_LEVEL` | `INFO` | 日志级别 |
| `RAG_API_KEY` | 空（必须注入） | 与后端 `PETHOME_RAG_API_KEY` 一致，请求头 `X-API-Key` |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama 地址 |
| `LLM_MODEL` | `deepseek-r1:7b` | 对话模型 |
| `LLM_TEMPERATURE` | `0.1` | 生成温度 |
| `OLLAMA_EMBEDDING_MODEL` | `bge-m3` | 向量模型 |
| `EMBEDDING_PROVIDER` | `ollama` | `ollama` / `huggingface` |
| `CHROMA_PERSIST_DIR` | `./chroma_db` | 索引持久化目录（相对路径） |
| `RETRIEVE_TOP_K` | `2` | 检索片段数 |
| `RETRIEVE_SCORE_THRESHOLD` | `0.5` | 相关性阈值，`0` 表示关闭 |
| `CHUNK_SIZE` / `CHUNK_OVERLAP` | `500` / `50` | 文本切分参数 |
| `HISTORY_MAX_ROUNDS` | `3` | 携带的历史轮数 |

### java-backend（系统环境变量）

| 变量 | 说明 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev`（默认）/ `prod` |
| `PETHOME_MYSQL_USERNAME` / `PETHOME_MYSQL_PASSWORD` | 数据库账号（dev 有默认值） |
| `PETHOME_REDIS_PASSWORD` | Redis 密码 |
| `PETHOME_RABBITMQ_USERNAME` / `PETHOME_RABBITMQ_PASSWORD` | MQ 账号 |
| `PETHOME_JWT_SECRET` | JWT 签名密钥，生产必填且 ≥ 32 字节 |
| `PETHOME_RAG_BASE_URL` / `PETHOME_RAG_API_KEY` | RAG 服务地址与鉴权 Key |
| `PETHOME_PAYMENT_CHANNEL` / `PETHOME_PAYMENT_ALLOW_MOCK` | 支付渠道与是否允许模拟支付 |
| `PETHOME_PAYMENT_CALLBACK_SECRET` | 支付回调验签密钥 |
| `PETHOME_UPLOAD_BASE_DIR` | 上传文件目录（默认 `./upload`） |
| `PETHOME_CORS_ALLOWED_ORIGINS` | 允许跨域来源，逗号分隔 |

## 接口调用示例

### 知识库问答

```bash
curl -X POST http://localhost:8000/api/knowledge/ask \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <RAG_API_KEY>" \
  -d '{
    "question": "猫咪多久洗一次澡比较合适？",
    "enabledDocIds": [343, 344, 345],
    "conversationHistory": [],
    "petInfo": {"breed": "英短", "age": 2, "weight": 4.5}
  }'
```

响应：

```json
{
  "answer": "……（正文）\n\n以上内容仅供科普参考，若宠物出现不适或症状严重，请及时前往宠物医院就医。",
  "references": [
    {"docId": 343, "title": "猫咪洗澡频率指南", "content": "……命中的片段……"}
  ]
}
```

### 重建向量索引

```bash
curl -X POST http://localhost:8000/api/knowledge/reindex \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <RAG_API_KEY>" \
  -d '{"documents": [{"id": 343, "title": "猫咪洗澡频率指南", "category": "猫咪", "content": "……"}]}'
```

### 用户登录与地址列表

```bash
# 1) 注册（手机号 + 密码）
curl -X POST http://localhost:8080/api/user/register \
  -H "Content-Type: application/json" \
  -d '{"phone": "13900001111", "password": "abc123456"}'

# 2) 登录拿 token
curl -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"phone": "13900001111", "password": "abc123456"}'

# 3) 携带 token 查询地址簿
curl http://localhost:8080/api/user/address/list \
  -H "Authorization: Bearer <token>"
```

## 目录结构

```
pet-home/
├── README.md
├── LICENSE
├── .gitignore
└── pet-home-service/
    ├── java-backend/            # Spring Boot 后端
    │   ├── src/main/java/com/pethome/
    │   │   ├── controller/      # C 端 / B 端接口
    │   │   ├── service/         # 业务实现
    │   │   ├── mapper/          # MyBatis-Plus Mapper
    │   │   ├── entity/ dto/ vo/ # 数据模型
    │   │   ├── rag/             # RAG 客户端与降级
    │   │   ├── payment/         # 支付渠道适配
    │   │   ├── mq/              # 延迟取消队列
    │   │   └── common/ config/  # 统一结果、异常、鉴权、配置
    │   ├── src/main/resources/db/migration  # Flyway 正式迁移脚本
    │   ├── src/main/resources/db/dev        # 演示数据（仅 dev）
    │   └── src/test/java/       # 396 个集成 / 单元测试
    ├── pet-home-h5/             # 用户端 H5
    ├── pet-home-admin/          # 管理后台
    └── rag-service/             # RAG 知识库问答服务
        ├── main.py              # FastAPI 入口（日志 / 异常 / 探活）
        ├── app/
        │   ├── api/             # 问答与索引重建接口
        │   ├── rag/             # Prompt、问答链、向量库、异常翻译
        │   ├── config/          # settings（环境变量）+ constants（常量）
        │   └── utils/           # X-API-Key 鉴权
        ├── tests/test_smoke.py  # 冒烟测试
        └── requirements.txt
```


