"""Chroma 向量库管理（LangChain 1.x）。

职责：
1. 提供向量模型（默认 ollama/bge-m3；可选 huggingface/bge-small-zh）
2. 文档切分与分批入库
3. 按 enabledDocIds 过滤后检索 top_k 个相关片段（可选相关性阈值）
4. 重建索引：清空后全量重建（reset_collection + 互斥锁，防并发破坏 collection）

与 0.3.x 的差异（已在 langchain-chroma 1.1.0 + chromadb 1.5.9 上实测）：
- Chroma 构造参数（collection_name / embedding_function / persist_directory /
  collection_metadata）保持不变；
- 清空 collection 改用官方 reset_collection()，不再自己开 chromadb 客户端；
- similarity_search(query, k, filter) 的 filter 语义不变，docId $in 过滤实测生效。
"""

import importlib.util
import logging
import threading
from functools import cached_property, lru_cache

from langchain_chroma import Chroma
from langchain_core.documents import Document
from langchain_core.embeddings import Embeddings
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.config.constants import (
    CHROMA_DISTANCE_SPACE,
    CHROMA_SPACE_METADATA_KEY,
    MIN_INDEX_BATCH_SIZE,
    MIN_RELEVANCE_SCORE,
)
from app.config.settings import Settings, get_settings
from app.rag.errors import RagEngineError, translate_engine_error
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
        """向量模型为懒加载：首次检索或重建索引时才初始化，避免启动即加载模型。"""
        provider = self._settings.embedding_provider.lower()

        if provider == "ollama":
            from langchain_ollama import OllamaEmbeddings

            logger.info(
                "初始化向量模型：Ollama(%s) @ %s", self._settings.ollama_embedding_model, self._settings.ollama_base_url
            )
            return OllamaEmbeddings(
                model=self._settings.ollama_embedding_model,
                base_url=self._settings.ollama_base_url,
            )

        if provider == "huggingface":
            return self._build_hf_embeddings()

        raise RagEngineError(f"不支持的 EMBEDDING_PROVIDER：{provider}（可选 ollama / huggingface）")

    def _build_hf_embeddings(self) -> Embeddings:
        """HuggingFace 分支：缺依赖时给出可直接执行的修复提示（默认路线无需 torch）。"""
        if importlib.util.find_spec("sentence_transformers") is None:
            raise RagEngineError(
                "HuggingFace 向量模型不可用：当前环境未安装 sentence-transformers（含 torch）。"
                "如需该路线请执行 pip install sentence-transformers；"
                "或保持默认 EMBEDDING_PROVIDER=ollama（需先 ollama pull bge-m3）"
            )

        embeddings_class = self._load_hf_embeddings_class()
        logger.info(
            "初始化向量模型：HuggingFace(%s) device=%s", self._settings.hf_embedding_model, self._settings.embedding_device
        )
        return embeddings_class(
            model_name=self._settings.hf_embedding_model,
            model_kwargs={"device": self._settings.embedding_device},
            # bge 系列官方建议：向量归一化后再计算余弦相似度
            encode_kwargs={"normalize_embeddings": True},
        )

    @staticmethod
    def _load_hf_embeddings_class() -> type[Embeddings]:
        """加载 HuggingFaceEmbeddings 类：优先新包 langchain-huggingface，兼容 langchain-community。

        :return: 可实例化的 Embeddings 实现类
        :raises RagEngineError: 两个包都未安装
        """
        try:
            from langchain_huggingface import HuggingFaceEmbeddings

            return HuggingFaceEmbeddings
        except ImportError:
            pass

        try:
            # langchain-community 0.4.1 仍保留该入口（懒加载，实例化时才需要 torch）
            from langchain_community.embeddings import HuggingFaceEmbeddings

            return HuggingFaceEmbeddings
        except ImportError as exc:
            raise RagEngineError(
                "未安装 langchain-huggingface / langchain-community，无法加载 HuggingFace 向量模型。"
                "请执行 pip install -r requirements.txt，或改用 EMBEDDING_PROVIDER=ollama"
            ) from exc

    def _embedding_model_name(self) -> str:
        """当前生效的向量模型名（用于日志与错误提示）。"""
        if self._settings.embedding_provider.lower() == "huggingface":
            return self._settings.hf_embedding_model
        return self._settings.ollama_embedding_model

    def _engine_error(self, exc: BaseException, stage: str) -> RagEngineError:
        """把底层异常翻译成可映射为 503 的引擎异常。"""
        return translate_engine_error(
            exc,
            base_url=self._settings.ollama_base_url,
            model=self._embedding_model_name(),
            stage=stage,
        )

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
        skipped = 0
        for item in documents:
            content = (item.content or "").strip()
            if not content:
                skipped += 1
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
        if skipped:
            logger.warning("切分时跳过 %s 篇正文为空的文档", skipped)
        return chunks

    # ---------------- Chroma ----------------

    def _create_store(self) -> Chroma:
        """创建（或复用）Chroma 实例；persist_directory 会在写入时自动持久化。"""
        return Chroma(
            collection_name=self._settings.chroma_collection,
            embedding_function=self.embeddings,
            persist_directory=self._settings.chroma_persist_dir,
            collection_metadata={CHROMA_SPACE_METADATA_KEY: CHROMA_DISTANCE_SPACE},
        )

    def _reset_collection(self) -> None:
        """清空 collection 并立即重建空集合。

        langchain-chroma 1.x 提供了 reset_collection()（内部 = delete_collection + 重新创建），
        实测在 collection 不存在时也不会抛异常，因此比自行维护 chromadb 客户端更稳。
        """
        try:
            self._create_store().reset_collection()
            logger.info("已清空并重建空 collection：%s", self._settings.chroma_collection)
        except Exception as exc:
            raise self._engine_error(exc, "清空向量库") from exc

    def _add_chunks(self, chunks: list[Document]) -> None:
        """分批入库，避免一次性提交过多片段导致请求体过大或内存峰值过高。"""
        batch_size = max(MIN_INDEX_BATCH_SIZE, self._settings.index_batch_size)
        total = len(chunks)
        store = self._create_store()
        for start in range(0, total, batch_size):
            group = chunks[start : start + batch_size]
            try:
                store.add_documents(group)
            except Exception as exc:
                raise self._engine_error(exc, "写入向量库") from exc
            logger.info("片段入库进度：%s/%s", min(start + batch_size, total), total)

    # ---------------- 对外能力 ----------------

    def rebuild(self, documents: list[DocumentItem]) -> tuple[int, int]:
        """清空向量库并按传入文档全量重建索引。

        :param documents: 待入库文档（一般由管理端传入 enabled=1 的文档）
        :return: (入库文档数, 生成的片段数)
        :raises RebuildInProgressError: 已有重建任务在执行
        :raises RagEngineError: 向量模型不可用（Ollama 未启动 / 模型未 pull）
        """
        if not self._rebuild_lock.acquire(blocking=False):
            raise RebuildInProgressError("索引重建正在进行中")

        try:
            self._reset_collection()
            chunks = self._split_documents(documents)
            if chunks:
                self._add_chunks(chunks)
            else:
                logger.warning("重建索引未生成任何片段：收到文档 %s 个（正文可能为空）", len(documents))
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
        store = self._create_store()
        threshold = self._settings.retrieve_score_threshold

        try:
            if threshold and threshold > 0:
                # 带分数的检索：把明显不相关的片段提前剔除（阈值 0 表示关闭该能力）
                scored = store.similarity_search_with_relevance_scores(question, k=k, filter=where)
                documents = [
                    document for document, score in scored if (score or MIN_RELEVANCE_SCORE) >= threshold
                ]
                logger.info(
                    "向量检索完成（相关性阈值 %.2f）：候选 %s 个，保留 %s 个",
                    threshold,
                    len(scored),
                    len(documents),
                )
            else:
                documents = store.similarity_search(question, k=k, filter=where)
                logger.info("向量检索完成：命中片段 %s 个", len(documents))
        except Exception as exc:
            raise self._engine_error(exc, "向量检索") from exc

        logger.info("本次检索范围：启用文档 %s 个", len(enabled_doc_ids))
        return documents


@lru_cache
def get_vector_store() -> VectorStoreManager:
    """获取全局唯一的向量库管理器。"""
    return VectorStoreManager(get_settings())
