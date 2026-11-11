"""宠享到家 - RAG 知识库问答服务入口。

技术栈：FastAPI + uvicorn + LangChain 1.x + Chroma + Ollama
（向量化默认走 Ollama 的 bge-m3，对话模型默认 deepseek-r1:1.5b，均可用环境变量覆盖）。

启动方式（二选一，需在 rag-service 目录下执行）::

    python main.py
    uvicorn main:app --host 0.0.0.0 --port 8000

端口与日志级别分别由环境变量 ``SERVER_PORT`` / ``LOG_LEVEL`` 控制。
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.api.knowledge import router as knowledge_router
from app.config.constants import DEFAULT_SERVER_HOST
from app.config.settings import get_settings

settings = get_settings()

#: 统一日志格式：时间 | 级别 | 模块 | 内容（全项目仅在此处初始化一次）
LOG_FORMAT = "%(asctime)s | %(levelname)-7s | %(name)s | %(message)s"

#: 422 状态码：Starlette 新版本把 HTTP_422_UNPROCESSABLE_ENTITY 改名为
#: HTTP_422_UNPROCESSABLE_CONTENT，这里按版本兼容取值，避免出现弃用告警
HTTP_422_UNPROCESSABLE = getattr(status, "HTTP_422_UNPROCESSABLE_CONTENT", 422)

logger = logging.getLogger("app")


def _configure_logging() -> None:
    """初始化根 logger（应用入口调用一次）。

    级别取自配置项 ``LOG_LEVEL``，非法值回退为 INFO，避免因配置笔误导致启动失败。
    """
    level = getattr(logging, settings.log_level.upper(), logging.INFO)
    logging.basicConfig(level=level, format=LOG_FORMAT)


# 模块导入即初始化日志（uvicorn main:app 与 python main.py 两种启动方式均生效）
_configure_logging()


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
    logger.info("监听端口：%s", settings.server_port)
    logger.info("向量模型为懒加载：首次调用 /api/knowledge/ask 或 /api/knowledge/reindex 时才初始化")
    logger.info("=" * 64)
    yield
    logger.info("%s 已停止", settings.app_name)


def _embedding_model_name() -> str:
    """当前生效的向量模型名。

    :return: huggingface 路线返回 HF 模型名，否则返回 Ollama 向量模型名。
    """
    if settings.embedding_provider.lower() == "huggingface":
        return settings.hf_embedding_model
    return settings.ollama_embedding_model


def _embedding_model_desc() -> str:
    """向量模型描述（用于启动日志）。

    :return: 带 provider / 设备信息的可读描述。
    """
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
    """探活接口（无需鉴权），供后端判断 RAG 服务是否可用。

    :return: 服务状态与当前生效的关键配置（不含任何密钥）。
    """
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
    """业务异常（401 未授权 / 409 索引重建中 等），透出可读提示。

    :param request: 当前请求
    :param exc: Starlette 抛出的 HTTP 异常
    :return: 统一结构的 JSON 错误响应
    """
    logger.warning("请求异常：%s %s -> %s %s", request.method, request.url.path, exc.status_code, exc.detail)
    return JSONResponse(
        status_code=exc.status_code,
        content={"code": exc.status_code, "message": str(exc.detail)},
    )


@app.exception_handler(RequestValidationError)
async def handle_validation_exception(request: Request, exc: RequestValidationError) -> JSONResponse:
    """请求参数校验失败。

    :param request: 当前请求
    :param exc: Pydantic 校验异常
    :return: 422 + 首条可读校验提示
    """
    message = _first_error_message(exc)
    logger.warning("参数校验失败：%s %s -> %s", request.method, request.url.path, message)
    return JSONResponse(
        status_code=HTTP_422_UNPROCESSABLE,
        content={"code": HTTP_422_UNPROCESSABLE, "message": f"请求参数不合法：{message}"},
    )


@app.exception_handler(Exception)
async def handle_unexpected_exception(request: Request, exc: Exception) -> JSONResponse:
    """兜底异常：完整堆栈只写入日志，对外统一返回友好提示。

    :param request: 当前请求
    :param exc: 未预期异常
    :return: 500 + 不含内部细节的提示
    """
    logger.exception("服务内部错误：%s %s", request.method, request.url.path)
    return JSONResponse(
        status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
        content={"code": status.HTTP_500_INTERNAL_SERVER_ERROR, "message": "服务内部错误，请稍后重试"},
    )


def _first_error_message(exc: RequestValidationError) -> str:
    """取第一条校验错误生成可读提示，例如：question 字段不能为空。

    :param exc: Pydantic 校验异常
    :return: 人类可读的提示文本
    """
    errors = exc.errors()
    if not errors:
        return "参数格式错误"

    first = errors[0]
    location = [str(item) for item in first.get("loc", ()) if item not in ("body", "query", "path")]
    # 整个请求体缺失时给出明确提示：例如 /api/knowledge/reindex 未传 documents
    if not location and first.get("type") == "missing":
        return "请求体不能为空"
    return f"{'.'.join(location)} {first.get('msg', '')}".strip()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host=DEFAULT_SERVER_HOST,
        port=settings.server_port,
        reload=False,  # 开发阶段可以改成 True 开启热重载
    )
