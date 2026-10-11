"""服务配置。

所有配置项均支持通过环境变量覆盖（变量名 = 字段名大写，不区分大小写），例如：

    RAG_API_KEY=xxx
    EMBEDDING_PROVIDER=ollama
    OLLAMA_BASE_URL=http://localhost:11434
    LLM_MODEL=deepseek-r1:1.5b
    CHROMA_PERSIST_DIR=./chroma_db

也可以复制 .env.example 为 .env 进行配置（环境变量优先级高于 .env 文件）。

向量化默认走 Ollama（bge-m3），因为该路线只依赖本 requirements.txt；
若要用 HuggingFace 的 bge-small-zh，需要额外安装 torch 与 sentence-transformers，
并把 embedding_provider 改为 huggingface。
"""

from functools import lru_cache
from typing import Literal

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """RAG 服务配置项。"""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # ---------------- 服务基础 ----------------
    app_name: str = "pet-home-rag-service"
    app_version: str = "1.1.0"
    log_level: str = "INFO"

    # ---------------- 鉴权 ----------------
    # 调用方需在请求头 X-API-Key 中携带该值
    rag_api_key: str = "pethome-rag-key"

    # ---------------- Ollama（对话模型 + 向量模型） ----------------
    ollama_base_url: str = "http://localhost:11434"
    # 本机已 pull 的对话模型
    llm_model: str = "deepseek-r1:1.5b"
    llm_temperature: float = 0.1
    # 是否保留推理模型的思考输出：
    #   False（默认）交给 langchain-ollama 的 reasoning 参数处理，不把 think 内容混进答案；
    #   代码里仍保留 think 标签剥离作为兜底（见 rag/chain.py）。
    llm_reasoning: bool = False
    # 单次生成的最大 token 数；-1 表示不限制（Ollama 默认值）
    llm_num_predict: int = -1

    # ---------------- 向量模型 ----------------
    # ollama：走 Ollama 的 embedding 接口（默认，依赖轻，无需 torch）
    # huggingface：本地推理 bge-small-zh（需安装 torch / sentence-transformers）
    embedding_provider: Literal["huggingface", "ollama"] = "ollama"
    hf_embedding_model: str = "BAAI/bge-small-zh-v1.5"
    # 本机已 pull 的向量模型（1024 维），中文检索效果优于 nomic-embed-text
    ollama_embedding_model: str = "bge-m3"
    embedding_device: str = "cpu"

    # ---------------- Chroma 向量库 ----------------
    chroma_persist_dir: str = "./chroma_db"
    chroma_collection: str = "knowledge_chunks"
    # 重建索引时分批入库的条数（避免一次性提交过多片段）
    index_batch_size: int = 100

    # ---------------- 检索与文本切分 ----------------
    # 与问题最相关的片段数量
    retrieve_top_k: int = 2
    # 相关性阈值（0~1，0 表示不过滤）：命中片段相似度低于该值时视为无可用知识。
    # 作用：把「与知识库无关」的提问（模型 1.5B 不一定会听话拒答）确定性地拦在模型之外。
    # 默认 0.5 来自 bge-m3 实测：宠物相关提问 top1 分数 0.73~0.81，无关提问 0.30~0.38，
    # 分界取 0.5 两侧都有余量；若发现正常提问被误拦，可调低该值。
    retrieve_score_threshold: float = 0.5
    chunk_size: int = 500
    chunk_overlap: int = 50

    # ---------------- 对话历史 ----------------
    # 最多携带最近 N 轮（一轮 = 一问一答）
    history_max_rounds: int = 3

    # ---------------- 固定话术 ----------------
    # 答案末尾固定拼接的免责声明
    disclaimer: str = "以上内容仅供科普参考，若宠物出现不适或症状严重，请及时前往宠物医院就医。"
    # 未检索到任何可用知识片段时的兜底回答（避免模型凭空作答）
    no_context_answer: str = "知识库中暂未收录相关内容，建议咨询专业宠物医生获取帮助。"


@lru_cache
def get_settings() -> Settings:
    """获取全局唯一的配置实例（进程内缓存）。"""
    return Settings()
