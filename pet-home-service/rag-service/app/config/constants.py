"""全局常量表。

这里集中存放「不随部署环境变化」的固定值，避免在业务代码里散落裸数字/裸字符串。
凡是需要按环境调整的参数（模型名、检索阈值、切分长度、超时等），
一律放在 :mod:`app.config.settings` 中通过环境变量读取。
"""

# ---------------- 服务 ----------------
#: 监听地址；容器 / 局域网访问都需要绑定 0.0.0.0
DEFAULT_SERVER_HOST = "0.0.0.0"

# ---------------- 向量库 ----------------
#: Chroma collection 元数据中指定距离函数的键名
CHROMA_SPACE_METADATA_KEY = "hnsw:space"
#: Chroma 的相似度计算方式（cosine：余弦距离，与 bge 系列归一化向量匹配）
CHROMA_DISTANCE_SPACE = "cosine"
#: 分批入库的最小批大小，防止配置成 0 / 负数导致 range 步长非法
MIN_INDEX_BATCH_SIZE = 1
#: 相关性分数的下界（分数缺失时按此值处理）
MIN_RELEVANCE_SCORE = 0.0
#: 片段缺少 docId 时使用的占位值
UNKNOWN_DOC_ID = 0

# ---------------- 对话与切片 ----------------
#: num_predict 取该值时表示不限制生成长度（Ollama 语义）
NO_TOKEN_LIMIT = -1
#: 一轮对话由「一问一答」两条消息组成
MESSAGES_PER_ROUND = 2
