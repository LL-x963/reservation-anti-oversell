# 08 RAG 知识库服务

## 1. 技术原理

### 1.1 为什么需要 RAG

直接问大模型「猫咪多久洗一次澡」，模型可能编造（幻觉）或给出与自家知识库不一致的说法。RAG（Retrieval-Augmented Generation，检索增强生成）的思路：

```
用户问题 → 向量检索出最相关的知识片段 → 把片段塞进 Prompt → 模型基于片段作答 → 返回答案 + 参考来源
```

好处：**答案可控**（有据可依）、**知识可更新**（改知识库即可，无需重训模型）、**可溯源**（返回 references）。

### 1.2 核心链路

1. **切分**：长文档按固定长度切块（chunk_size），块间保留重叠（overlap）避免语义被切断；
2. **向量化**：用 embedding 模型把每个块编码成向量；
3. **存储**：向量库（本项目用 Chroma）持久化；
4. **检索**：把问题也向量化，按相似度取 top_k；
5. **生成**：组装 Prompt（系统指令 + 参考片段 + 历史对话 + 用户问题）交给 LLM。

### 1.3 LangChain 1.x 的模块化

LangChain 拆成了多个包，按需安装（`langchain-core` 是内核，具体集成各自成包）：本项目的依赖清单是

```
langchain 1.2.12 / langchain-core 1.2.18 / langchain-community 0.4.1
langchain-text-splitters 1.1.1 / langchain-chroma 1.1.0 / langchain-ollama 1.0.1
fastapi 0.135.1 / uvicorn 0.46.0 / pydantic 2.12.5 / pydantic-settings 2.12.0
```

**注意**：1.x 与 0.3.x 的 API 有差异（导入路径、链式写法、Ollama 的 `reasoning` 参数等），照抄网上 0.3 时代的代码容易踩坑。

## 2. 本项目怎么用

### 2.1 职责边界

| 谁 | 负责 |
|---|---|
| **Java 后端** | 鉴权（用户 token）、宠物上下文组装与归属校验、**可检索文档白名单**（`enabled=1 AND index_status=2`）、历史落库、超时重试与**降级** |
| **rag-service** | 切分/向量化/索引重建、向量检索、Prompt 组装、调用 Ollama、返回答案与参考来源 |

浏览器**不能**直接访问 8000 端口（走 Java 中转），接口用 `X-API-Key` 校验：

```python
# rag-service/main.py / app/api/knowledge.py
POST /api/knowledge/ask      # {question, userId, conversationHistory, petInfo, enabledDocIds} → {answer, references[]}
POST /api/knowledge/reindex  # {documents:[{id,title,category,content}]} → {documentCount, chunkCount}
GET  /health                 # {status, version}
```

### 2.2 关键配置（`rag-service/app/config/settings.py`）

| 配置 | 默认值 | 说明 |
|---|---|---|
| `llm_model` | `deepseek-r1:1.5b` | 本机已 pull 的对话模型（小模型，秒级出结果） |
| `embedding_provider` | `ollama` | 走 Ollama 的 embedding 接口，**不需要 torch**（HF 方案需装 torch + sentence-transformers，约 2GB） |
| `ollama_embedding_model` | `bge-m3`（1024 维） | 中文检索效果优于 `nomic-embed-text` |
| `chroma_persist_dir` / `chroma_collection` | `./chroma_db` / `knowledge_chunks` | 向量库位置与集合名 |
| `retrieve_top_k` | 2 | 取最相关的 2 个片段 |
| **`retrieve_score_threshold`** | **0.5** | **相似度阈值，低于它视为「无关问题」直接兜底拒答** |
| `chunk_size` / `chunk_overlap` | 500 / 50 | 切分粒度 |
| `history_max_rounds` | 3 | 携带最近 3 轮对话 |
| `disclaimer` / `no_context_answer` | 固定话术 | 免责声明 / 无命中兜底 |

**阈值为什么是 0.5（这是本项目的一个实测结论）**：宠物相关提问的 top1 相似度落在 **0.73~0.81**，无关提问落在 **0.30~0.38**，取 0.5 两侧都有余量。**作用**：1.5B 小模型不一定会「听话拒答」，用确定性的阈值过滤把它拦在模型之外，避免瞎编。

### 2.3 问答链路（`app/rag/chain.py`）

```python
def ask(self, request) -> tuple[str, list[Reference]]:
    documents = self._vector_store.search(...)      # 1. 检索（已按 enabledDocIds 过滤 + 阈值过滤）
    ...                                             # 2. 组装 Prompt（系统指令 + 片段 + 历史 + 问题）
    answer = self._sanitize(answer)                 # 3. 剥离推理模型的 think 标签
    if not answer: answer = self._settings.no_context_answer
    return self._with_disclaimer(answer), self._to_references(documents)   # 4. 拼免责声明
```

- **think 标签剥离**：`deepseek-r1` 类推理模型会输出思考过程，配置 `llm_reasoning=False` 交给 `langchain-ollama` 处理，代码里仍保留剥离逻辑作为兜底；
- **免责声明**：`_with_disclaimer` 做了「已包含则不重复追加」的保护。

### 2.4 索引重建（全量）

```
管理端点击「重建索引」
  → Java 侧 POST /api/admin/knowledge/doc/reindex
  → 把启用文档 index_status 置 1（索引中）并异步调用 rag-service /reindex（清空集合 → 分批入库 100 条/批）
  → 成功置 2（正常）/ 失败置 3（失败）
  → 前端轮询 GET /api/admin/knowledge/doc/indexStatus，看 rebuilding 转 false
```

**重建是"全量替换"**：`/reindex` 会先清空集合再写入，所以「文档内容改了但没重建索引」时检索到的还是旧内容——这是「编辑文档后要提示用户重建索引」的原因。

### 2.5 Java 侧客户端（`rag/KnowledgeClient.java`）

- 两个 RestTemplate：问答（读超时 30s）与重建（读超时 5min），超时可配置；
- 失败重试 1 次，最终失败抛 `KnowledgeServiceException` → 全局异常处理成 `code=0, bizCode=600`；
- **显式使用 `SimpleClientHttpRequestFactory`**（原因见下方踩坑 1）。

## 3. 易错点与实际踩过的坑

1. **JDK HttpClient 丢 POST 请求体（本项目最难排查的一个 bug）**
   现象：管理端「重建索引」永远失败，Java 日志是 `status=422`、Python 侧提示「请求体不能为空」，但 Java 明明序列化出了 92 字节 JSON。
   定位过程：起了个带抓包中间件的诊断实例（独立端口 + 独立 chroma 目录），对比发现——**默认工厂**发出的请求 `Transfer-Encoding: chunked` 且服务端收到 **0 字节**；换成 `SimpleClientHttpRequestFactory` 后服务端收到完整 138 字节并返回 200。同时用 Python 以 chunked 发同样请求能成功，**排除了服务端问题**。
   根因：Spring Boot 3.4+ 的 `RestTemplateBuilder` 在类路径没有 Apache HttpClient/OkHttp 时默认选 **`JdkClientHttpRequestFactory`**，该工厂在本场景下没有把请求体真正写出去。
   修复（`config/KnowledgeClientConfig.java`）：

   ```java
   return builder
           .requestFactory(SimpleClientHttpRequestFactory::new)   // 关键：换回会缓冲请求体的工厂
           .connectTimeout(properties.getConnectTimeout())
           .readTimeout(properties.getReindexReadTimeout())
           .build();
   ```

2. **"空 body"这类问题要把两侧请求都抓出来对比**：只盯一端会得出错误结论（我一开始甚至怀疑 Python 不认 chunked）。经验：**客户端序列化结果 → 中间抓包 → 服务端收到的原始字节**，三层对齐最快。

3. **换 embedding 模型必须重建索引**：Chroma 集合的向量维度由首次写入决定（`bge-m3` 是 1024 维），换模型（维度不同）必须清空重建，否则检索直接报错。

4. **推理模型的思考内容会混进答案**：必须靠 `reasoning=False` + `think` 标签剥离双保险，否则用户在聊天框里会看到大段推理过程。

5. **免责声明只应出现一次**：RAG 侧已在答案末尾拼接；H5 端又要求单独展示灰色小字，所以前端渲染前会**剥离后端那段**再自己渲染（见 [10 章](10-h5-frontend.md)）。两端都要改文案时记得同步。

6. **`X-API-Key` 两边必须一致**：Java 侧 `pethome.knowledge.api-key` 与 rag-service 的 `RAG_API_KEY` 默认都是 `pethome-rag-key`；不一致的表现是 401（而不是业务错误），容易被误判为「服务挂了」。

7. **RAG 服务不可用时后端必须降级而不是报错崩**：`KnowledgeServiceException` 会被映射成 `code=0 + bizCode=600`，H5 端展示「知识库服务暂时繁忙，请稍后再试」而不是弹一堆技术错误。**AI 依赖故障不应该影响下单等主流程**。
