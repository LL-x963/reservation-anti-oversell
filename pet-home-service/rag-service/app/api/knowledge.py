"""知识库接口：问答与索引重建"""

import logging

from fastapi import APIRouter, Depends, HTTPException, status

from app.rag.chain import get_rag_chain
from app.rag.errors import RagEngineError
from app.rag.vector_store import RebuildInProgressError, get_vector_store
from app.schemas import AskRequest, AskResponse, ReindexRequest, ReindexResponse
from app.utils.auth import verify_api_key

logger = logging.getLogger(__name__)

# 整个路由统一校验 X-API-Key
router = APIRouter(
    prefix="/api/knowledge",
    tags=["知识库"],
    dependencies=[Depends(verify_api_key)],
)


@router.post("/ask", response_model=AskResponse, summary="知识库问答")
def ask(request: AskRequest) -> AskResponse:
    """基于知识库回答宠物相关问题。

    处理流程：校验 X-API-Key -> 按 enabledDocIds 检索 2 个片段 -> 拼接 Prompt
    -> 调用 Ollama 生成答案 -> 末尾拼接免责声明 -> 返回答案与参考来源。
    引擎不可用时返回 503 + 可读提示（不含内部堆栈）。
    """
    logger.info(
        "收到提问：userId=%s，enabledDocIds=%s，历史消息=%s 条，question=%s",
        request.user_id,
        request.enabled_doc_ids,
        len(request.conversation_history),
        request.question,
    )

    # 说明：这里刻意使用同步函数（def 而非 async def），FastAPI 会把它放到线程池执行，
    # 避免向量检索与模型推理这类阻塞操作卡住事件循环。
    try:
        answer, references = get_rag_chain().ask(request)
    except RagEngineError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=exc.public_message,
        ) from exc

    return AskResponse(answer=answer, references=references)


@router.post("/reindex", response_model=ReindexResponse, summary="重建向量索引")
def reindex(request: ReindexRequest) -> ReindexResponse:
    """清空向量库并按传入文档全量重建索引（供后端管理端调用）。

    后端只需把 enabled=1 的知识库文档（id/title/category/content）传过来即可。
    并发重建返回 409；向量模型不可用返回 503。
    """
    logger.info("收到重建索引请求：文档 %s 个", len(request.documents))

    try:
        document_count, chunk_count = get_vector_store().rebuild(request.documents)
    except RebuildInProgressError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="索引重建正在进行中，请稍后再试",
        ) from exc
    except RagEngineError as exc:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=exc.public_message,
        ) from exc

    return ReindexResponse(document_count=document_count, chunk_count=chunk_count)
