"""RAG 引擎异常与统一翻译"""

import logging

logger = logging.getLogger(__name__)

# 引擎异常类型：unavailable 服务不可达 / timeout 超时 / model 模型不可用 / unknown 未知
ENGINE_UNAVAILABLE = "unavailable"
ENGINE_TIMEOUT = "timeout"
ENGINE_MODEL = "model"
ENGINE_UNKNOWN = "unknown"


class RagEngineError(RuntimeError):
    """RAG 引擎不可用（连不上 Ollama、超时、模型不存在等），统一映射为 503。"""

    def __init__(self, message: str, *, kind: str = ENGINE_UNKNOWN) -> None:
        """构造引擎异常。

        :param message: 面向调用方的可读提示（不含内部堆栈）
        :param kind: 异常类型，取值见模块级 ``ENGINE_*`` 常量
        """
        super().__init__(message)
        self.kind = kind
        # 面向调用方的可读提示（内部细节已在上层日志中记录）
        self.public_message = message


def translate_engine_error(exc: BaseException, *, base_url: str, model: str, stage: str) -> RagEngineError:
    """把底层异常翻译为 RagEngineError。

    :param exc: 原始异常
    :param base_url: Ollama 服务地址（用于提示）
    :param model: 模型名（用于提示）
    :param stage: 出错阶段描述，如「生成回答」「计算向量」
    """
    name = type(exc).__name__
    text = str(exc)
    lowered = text.lower()

    # 1. 连接类：Ollama 没启动 / 端口不对 / DNS 失败
    if "connecterror" in name.lower() or "connectionerror" in name.lower() or "connecttimeout" in name.lower():
        logger.error("%s失败：无法连接 Ollama(%s)：%s", stage, base_url, text)
        return RagEngineError(
            f"{stage}失败：无法连接 Ollama 服务（{base_url}），请确认 ollama serve 已启动",
            kind=ENGINE_UNAVAILABLE,
        )

    # 2. 超时类：模型推理过慢（CPU 跑 1.5B 也可能到十几秒）
    if "timeout" in name.lower():
        logger.error("%s失败：Ollama 响应超时：%s", stage, text)
        return RagEngineError(f"{stage}失败：Ollama 响应超时，请稍后重试或改用更小的模型", kind=ENGINE_TIMEOUT)

    # 3. 模型类：模型不存在（未 ollama pull）
    if "not found" in lowered or "responseerror" in lowered:
        logger.error("%s失败：模型不可用(%s)：%s", stage, model, text)
        return RagEngineError(
            f"{stage}失败：Ollama 模型不可用（{model}），请先执行 ollama pull {model}",
            kind=ENGINE_MODEL,
        )

    # 4. 其余：保持原始信息，由日志记录完整堆栈
    logger.exception("%s失败：未知异常", stage)
    return RagEngineError(f"{stage}失败，请稍后重试", kind=ENGINE_UNKNOWN)
