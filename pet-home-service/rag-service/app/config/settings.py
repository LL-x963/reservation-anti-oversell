"""服务配置。

所有配置项均支持通过环境变量覆盖（变量名 = 字段名大写，不区分大小写），例如：

    RAG_API_KEY=xxx
    EMBEDDING_PROVIDER=ollama
    OLLAMA_BASE_URL=http://localhost:11434
    LLM_MODEL=deepseek-r1:1.5b

也可以复制 .env.example 为 .env 进行配置（环境变量优先级高于 .env 文件）。
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
    app_version: str = "1.0.0"
    log_level: str = "INFO"

    # ---------------- 鉴权 ----------------
    # 调用方需在请求头 X-API-Key 中携带该值
    rag_api_key: str = "pethome-rag-key"

    # ---------------- Ollama（对话模型 + 可选向量模型） ----------------
    ollama_base_url: str = "http://localhost:11434"
    # 本机已安装的模型为 deepseek-r1:1.5b
    llm_model: str = "deepseek-r1:1.5b"
    llm_temperature: float = 0.1

    # ---------------- 向量模型 ----------------
    # huggingface：本地推理 bge-small-zh（需安装 torch / sentence-transformers）
    # ollama：走 Ollama 的 embedding 接口（依赖轻，但模型不是 bge-small-zh）
    embedding_provider: Literal["huggingface", "ollama"] = "huggingface"
    hf_embedding_model: str = "BAAI/bge-small-zh-v1.5"
    ollama_embedding_model: str = "bge-m3"
    embedding_device: str = "cpu"

    # ---------------- Chroma 向量库 ----------------
    chroma_persist_dir: str = "./chroma_db"
    chroma_collection: str = "knowledge_chunks"

    # ---------------- 检索与文本切分 ----------------
    # 与问题最相关的片段数量
    retrieve_top_k: int = 2
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
