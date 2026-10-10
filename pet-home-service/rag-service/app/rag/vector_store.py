"""Chroma 向量库管理。

职责：
1. 提供向量模型（huggingface：bge-small-zh 本地推理 / ollama）
2. 文档切分与入库
3. 按 enabledDocIds 过滤后检索 top_k 个相关片段
4. 重建索引：接收文档列表，清空后全量重建
"""

import logging
import threading
from functools import cached_property, lru_cache

import chromadb
from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_core.embeddings import Embeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.config.settings import Settings, get_settings
from app.schemas import DocumentItem

logger = logging.getLogger(__name__)


class RebuildInProgressError(RuntimeError):
    """索引重建正在进行中（并发重建会破坏 collection，因此需要互斥）。"""


class VectorStoreManager:
    """Chroma 向量库管理器（进程内单例，见 get_vector_store）。"""

    def __init__(self, settings: Settings) -> None:
        self._settings = settings
        self._rebuild_lock = threading.Lock()

    # ---------------- 向量模型 ----------------

    @cached_property
    def embeddings(self) -> Embeddings:
        """向量模型为懒加载：首次检索或重建索引时才初始化，避免启动即下载模型。"""
        provider = self._settings.embedding_provider.lower()

        if provider == "ollama":
            from langchain_ollama import OllamaEmbeddings

            logger.info("初始化向量模型：Ollama(%s)", self._settings.ollama_embedding_model)
            return OllamaEmbeddings(
                model=self._settings.ollama_embedding_model,
                base_url=self._settings.ollama_base_url,
            )

        if provider == "huggingface":
            embeddings_class = self._load_hf_embeddings_class()
            logger.info("初始化向量模型：HuggingFace(%s)", self._settings.hf_embedding_model)
            return embeddings_class(
                model_name=self._settings.hf_embedding_model,
                model_kwargs={"device": self._settings.embedding_device},
                # bge 系列官方建议：向量归一化后再计算余弦相似度
                encode_kwargs={"normalize_embeddings": True},
            )

        raise RuntimeError(f"不支持的 EMBEDDING_PROVIDER：{provider}（可选 huggingface / ollama）")

    @staticmethod
    def _load_hf_embeddings_class():
        """加载 HuggingFaceEmbeddings 类：优先新包 langchain-huggingface，兼容 langchain-community。"""
        try:
            from langchain_huggingface import HuggingFaceEmbeddings

            return HuggingFaceEmbeddings
        except ImportError:
            try:
                from langchain_community.embeddings import HuggingFaceEmbeddings

                return HuggingFaceEmbeddings
            except ImportError as exc:
                raise RuntimeError(
                    "未安装 sentence-transformers / langchain-huggingface，无法使用 bge-small-zh。"
                    "请执行 pip install -r requirements.txt，"
                    "或将 EMBEDDING_PROVIDER 切换为 ollama（需先执行 ollama pull bge-m3）。"
                ) from exc

    # ---------------- 文本切分 ----------------

    @cached_property
    def text_splitter(self) -> RecursiveCharacterTextSplitter:
        """按段落与中文标点切分，尽量保持语义完整。"""
        return RecursiveCharacterTextSplitter(
            chunk_size=self._settings.chunk_size,
            chunk_overlap=self._settings.chunk_overlap,
            separators=["\n\n", "\n", "。", "！", "？", "；", "，", " ", ""],
        )

    def _split_documents(self, documents: list[DocumentItem]) -> list[Document]:
        """把文档切分为带来源元数据的片段（docId / title / category）。"""
        chunks: list[Document] = []
        for item in documents:
            content = (item.content or "").strip()
            if not content:
                continue
            metadata = {
                "docId": item.id,
                "title": item.title or "",
                "category": item.category or "",
            }
            for piece in self.text_splitter.split_text(content):
                text = piece.strip()
                if text:
                    chunks.append(Document(page_content=text, metadata=dict(metadata)))
        return chunks

    # ---------------- Chroma ----------------

    def _create_store(self) -> Chroma:
        """创建（或复用）Chroma 实例；persist_directory 会在写入时自动持久化。"""
        return Chroma(
            collection_name=self._settings.chroma_collection,
            embedding_function=self.embeddings,
            persist_directory=self._settings.chroma_persist_dir,
            collection_metadata={"hnsw:space": "cosine"},
        )

    def _reset_collection(self) -> None:
        """删除已有 collection（若存在），保证重建是全量覆盖语义。"""
        client = chromadb.PersistentClient(path=self._settings.chroma_persist_dir)
        # 不同 chromadb 版本 list_collections 可能返回 Collection 对象或名称字符串，这里统一处理
        existing = {
            collection if isinstance(collection, str) else collection.name
            for collection in client.list_collections()
        }
        if self._settings.chroma_collection in existing:
            client.delete_collection(self._settings.chroma_collection)
            logger.info("已删除旧 collection：%s", self._settings.chroma_collection)
        else:
            logger.info("collection 尚不存在，跳过删除：%s", self._settings.chroma_collection)

    # ---------------- 对外能力 ----------------

    def rebuild(self, documents: list[DocumentItem]) -> tuple[int, int]:
        """清空向量库并按传入文档全量重建索引。

        :param documents: 待入库文档（一般由管理端传入 enabled=1 的文档）
        :return: (入库文档数, 生成的片段数)
        :raises RebuildInProgressError: 已有重建任务在执行
        """
        if not self._rebuild_lock.acquire(blocking=False):
            raise RebuildInProgressError("索引重建正在进行中")

        try:
            self._reset_collection()
            chunks = self._split_documents(documents)
            if chunks:
                self._create_store().add_documents(chunks)
            logger.info("索引重建完成：文档 %s 个，片段 %s 个", len(documents), len(chunks))
            return len(documents), len(chunks)
        finally:
            self._rebuild_lock.release()

    def search(self, question: str, enabled_doc_ids: list[int], top_k: int | None = None) -> list[Document]:
        """先按 enabledDocIds 过滤，再检索最相关的 top_k 个片段。

        使用 Chroma 元数据过滤（docId $in enabledDocIds）而不是“先检索再过滤”，
        这样既能保证片段一定来自允许的文档，也能凑满 top_k 条。

        :param question: 用户问题
        :param enabled_doc_ids: 允许检索的文档 ID；为空表示没有可用知识
        :param top_k: 取前 k 条，默认取配置项 retrieve_top_k
        """
        if not enabled_doc_ids:
            logger.info("enabledDocIds 为空，跳过向量检索")
            return []

        k = top_k or self._settings.retrieve_top_k
        where = {"docId": {"$in": list(enabled_doc_ids)}}
        documents = self._create_store().similarity_search(question, k=k, filter=where)
        logger.info("向量检索完成：启用文档 %s 个，命中片段 %s 个", len(enabled_doc_ids), len(documents))
        return documents


@lru_cache
def get_vector_store() -> VectorStoreManager:
    """获取全局唯一的向量库管理器。"""
    return VectorStoreManager(get_settings())
