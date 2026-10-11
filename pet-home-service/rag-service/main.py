"""宠享到家 - RAG 知识库问答服务入口。

技术栈：FastAPI + uvicorn + LangChain + Chroma + Ollama + bge-small-zh

启动命令：
    uvicorn main:app --host 0.0.0.0 --port 8000
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.api.knowledge import router as knowledge_router
from app.config.settings import get_settings

settings = get_settings()

logging.basicConfig(
    level=getattr(logging, settings.log_level.upper(), logging.INFO),
    format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
)
logger = logging.getLogger("app")


@asynccontextmanager
async def lifespan(_: FastAPI):
    """启动 / 关闭时打印关键配置，便于排查环境问题。"""
    logger.info("=" * 64)
    logger.info("%s v%s 启动中", settings.app_name, settings.app_version)
    logger.info(
        "对话模型：%s @ %s（temperature=%s，reasoning=%s）",
        settings.llm_model,
        settings.ollama_base_url,
        settings.llm_temperature,
        settings.llm_reasoning,
    )
    logger.info("向量模型：%s", _embedding_model_desc())
    logger.info(
        "Chroma 持久化目录：%s，collection：%s，分批入库 %s 条",
        settings.chroma_persist_dir,
        settings.chroma_collection,
        settings.index_batch_size,
    )
    logger.info(
        "检索 top_k=%s，相关性阈值=%s（0 表示关闭），切分 chunk_size=%s / overlap=%s，历史轮数=%s",
        settings.retrieve_top_k,
        settings.retrieve_score_threshold,
        settings.chunk_size,
        settings.chunk_overlap,
        settings.history_max_rounds,
    )
    logger.info("向量模型为懒加载：首次调用 /api/knowledge/ask 或 /api/knowledge/reindex 时才初始化")
    logger.info("=" * 64)
    yield
    logger.info("%s 已停止", settings.app_name)


def _embedding_model_name() -> str:
    """当前生效的向量模型名。"""
    if settings.embedding_provider.lower() == "huggingface":
        return settings.hf_embedding_model
    return settings.ollama_embedding_model


def _embedding_model_desc() -> str:
    """向量模型描述（用于启动日志）。"""
    if settings.embedding_provider.lower() == "huggingface":
        return f"huggingface（{settings.hf_embedding_model}，device={settings.embedding_device}）"
    return f"ollama（{settings.ollama_embedding_model}）"


app = FastAPI(
    title="宠享到家 RAG 知识库问答服务",
    version=settings.app_version,
    lifespan=lifespan,
)

app.include_router(knowledge_router)


@app.get("/health", tags=["系统"], summary="健康检查")
def health() -> dict:
    """探活接口（无需鉴权），供后端判断 RAG 服务是否可用。"""
    return {
        "status": "ok",
        "app": settings.app_name,
        "version": settings.app_version,
        "llmModel": settings.llm_model,
        "embeddingProvider": settings.embedding_provider,
        "embeddingModel": _embedding_model_name(),
        "ollamaBaseUrl": settings.ollama_base_url,
        "chromaCollection": settings.chroma_collection,
        "retrieveTopK": settings.retrieve_top_k,
    }


# ============================================================
# 全局异常处理：统一错误结构，内部错误不对外暴露堆栈
# ============================================================


@app.exception_handler(StarletteHTTPException)
async def handle_http_exception(request: Request, exc: StarletteHTTPException) -> JSONResponse:
    """业务异常（401 未授权 / 409 索引重建中 等），透出可读提示。"""
    logger.warning("请求异常：%s %s -> %s %s", request.method, request.url.path, exc.status_code, exc.detail)
    return JSONResponse(
        status_code=exc.status_code,
        content={"code": exc.status_code, "message": str(exc.detail)},
    )


@app.exception_handler(RequestValidationError)
async def handle_validation_exception(request: Request, exc: RequestValidationError) -> JSONResponse:
    """请求参数校验失败。"""
    message = _first_error_message(exc)
    logger.warning("参数校验失败：%s %s -> %s", request.method, request.url.path, message)
    return JSONResponse(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        content={"code": status.HTTP_422_UNPROCESSABLE_ENTITY, "message": f"请求参数不合法：{message}"},
    )


@app.exception_handler(Exception)
async def handle_unexpected_exception(request: Request, exc: Exception) -> JSONResponse:
    """兜底异常：完整堆栈只写入日志，对外统一返回友好提示。"""
    logger.exception("服务内部错误：%s %s", request.method, request.url.path)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"code": status.HTTP_500_INTERNAL_SERVER_ERROR, "message": "服务内部错误，请稍后重试"},
    )


def _first_error_message(exc: RequestValidationError) -> str:
    """取第一条校验错误生成可读提示，例如：question 字段不能为空。"""
    errors = exc.errors()
    if not errors:
        return "参数格式错误"

    first = errors[0]
    location = ".".join(str(item) for item in first.get("loc", ()) if item not in ("body", "query", "path"))
    return f"{location} {first.get('msg', '')}".strip()
