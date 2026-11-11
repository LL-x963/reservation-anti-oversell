"""RAG 问答链：检索 -> 拼接 Prompt -> 调用 Ollama -> 返回答案与参考来源（LangChain 1.x）"""

import logging
import re
from functools import lru_cache

from langchain_core.documents import Document
from langchain_core.language_models import BaseChatModel
from langchain_core.output_parsers import StrOutputParser
from langchain_core.runnables import Runnable

from app.config.constants import NO_TOKEN_LIMIT, UNKNOWN_DOC_ID
from app.config.settings import Settings, get_settings
from app.rag.errors import translate_engine_error
from app.rag.prompt import build_prompt_template, format_context, format_history, format_pet_info
from app.rag.vector_store import VectorStoreManager, get_vector_store
from app.schemas import AskRequest, Reference

logger = logging.getLogger(__name__)

# deepseek-r1 等推理模型有时会把思考过程包在  thinking 标签中，需要剥离后再返回
_THINK_BLOCK = re.compile(r"<think(?:ing)?>.*?</think(?:ing)?>", re.DOTALL | re.IGNORECASE)


class RagChain:
    """知识库问答链。"""

    def __init__(self, settings: Settings, vector_store: VectorStoreManager) -> None:
        """初始化问答链。

        :param settings: 服务配置（模型名、阈值、话术等）
        :param vector_store: 向量库管理器，负责检索知识片段
        """
        self._settings = settings
        self._vector_store = vector_store
        # LCEL 链：Prompt -> Ollama -> 纯文本输出
        self._chain: Runnable = build_prompt_template() | self._build_llm() | StrOutputParser()

    def _build_llm(self) -> BaseChatModel:
        """构建 Ollama 对话模型（初始化时不发请求，首次调用才连接）。"""
        from langchain_ollama import ChatOllama

        extra: dict = {}
        # num_predict 为 -1（NO_TOKEN_LIMIT）时不传，交由 Ollama 使用默认上限
        if self._settings.llm_num_predict is not None and self._settings.llm_num_predict > NO_TOKEN_LIMIT:
            extra["num_predict"] = self._settings.llm_num_predict

        logger.info(
            "初始化对话模型：Ollama(%s) @ %s，temperature=%s，reasoning=%s",
            self._settings.llm_model,
            self._settings.ollama_base_url,
            self._settings.llm_temperature,
            self._settings.llm_reasoning,
        )
        return ChatOllama(
            model=self._settings.llm_model,
            base_url=self._settings.ollama_base_url,
            temperature=self._settings.llm_temperature,
            # langchain-ollama 1.0 起支持：控制推理内容是否进入答案
            reasoning=self._settings.llm_reasoning,
            **extra,
        )

    def ask(self, request: AskRequest) -> tuple[str, list[Reference]]:
        """执行一次问答。

        :return: (回答内容（末尾已拼免责声明）, 参考来源列表)
        :raises RagEngineError: Ollama 不可用 / 超时 / 模型未 pull
        """
        # 1. 检索（已按 enabledDocIds 过滤）
        documents = self._vector_store.search(
            question=request.question,
            enabled_doc_ids=request.enabled_doc_ids,
            top_k=self._settings.retrieve_top_k,
        )

        # 2. 没有可用知识片段时直接兜底，避免模型凭空编造（系统约束要求只能基于知识库作答）
        if not documents:
            logger.info("未检索到可用知识片段，直接返回兜底回答")
            return self._with_disclaimer(self._settings.no_context_answer), []

        # 3. 组装 Prompt：系统约束 + 宠物信息 + 最近 N 轮历史 + 知识片段 + 问题
        try:
            answer = self._chain.invoke(
                {
                    "pet_info": format_pet_info(request.pet_info),
                    "history": format_history(request.conversation_history, self._settings.history_max_rounds),
                    "context": format_context(documents),
                    "question": request.question,
                }
            )
        except Exception as exc:
            raise translate_engine_error(
                exc,
                base_url=self._settings.ollama_base_url,
                model=self._settings.llm_model,
                stage="生成回答",
            ) from exc

        # 4. 清理（剥离推理标签）并拼接免责声明
        answer = self._sanitize(answer)
        if not answer:
            logger.warning("模型返回空答案，改用兜底回答")
            answer = self._settings.no_context_answer

        logger.info("问答完成：命中片段 %s 个，答案长度 %s", len(documents), len(answer))
        return self._with_disclaimer(answer), self._to_references(documents)

    def _with_disclaimer(self, answer: str) -> str:
        """答案末尾固定拼接免责声明（若已包含则不重复追加）。"""
        text = (answer or "").strip()
        if self._settings.disclaimer in text:
            return text
        return f"{text}\n\n{self._settings.disclaimer}"

    @staticmethod
    def _sanitize(answer: str | None) -> str:
        """去掉推理标签与首尾空白。"""
        return _THINK_BLOCK.sub("", answer or "").strip()

    @staticmethod
    def _to_references(documents: list[Document]) -> list[Reference]:
        """把命中的片段转成对外参考来源。"""
        references: list[Reference] = []
        for document in documents:
            metadata = document.metadata or {}
            references.append(
                Reference(
                    doc_id=int(metadata.get("docId") or UNKNOWN_DOC_ID),
                    title=str(metadata.get("title") or ""),
                    content=document.page_content.strip(),
                )
            )
        return references


@lru_cache
def get_rag_chain() -> RagChain:
    """获取全局唯一的问答链。"""
    return RagChain(get_settings(), get_vector_store())
