"""RAG 服务冒烟测试（零第三方依赖，使用标准库 unittest）。

覆盖范围：
1. ``GET /health`` 探活
2. ``POST /api/knowledge/ask`` 缺少 X-API-Key 时返回 401
3. ``POST /api/knowledge/ask`` 参数校验失败时返回 422
4. （可选）真实问答链路，需显式开启：``RAG_SMOKE_ASK=1``

运行方式（先启动服务）::

    python tests/test_smoke.py
    python -m unittest discover -s tests -v

可用环境变量：
    RAG_BASE_URL   服务地址，默认 http://127.0.0.1:8000
    RAG_API_KEY    与服务端一致的 X-API-Key
    RAG_SMOKE_ASK  设为 1 时额外执行真实问答（需要 Ollama 与已建索引，耗时较长）
"""

import json
import logging
import os
import unittest
import urllib.error
import urllib.request

logger = logging.getLogger("smoke")

#: 默认服务地址（与 settings.server_port 默认值一致）
DEFAULT_BASE_URL = "http://127.0.0.1:8000"
#: 单次请求超时（秒）；问答链路由 Ollama 驱动，单独给更长的超时
DEFAULT_TIMEOUT_SECONDS = 5
#: 真实问答的超时（秒），CPU 跑 1.5B 模型可能需要十几秒
ASK_TIMEOUT_SECONDS = 120
#: 敏感配置的示例默认值（仅本地联调），与 .env.example 保持一致
DEFAULT_API_KEY = "pethome-rag-key"

HTTP_OK = 200
HTTP_UNAUTHORIZED = 401
HTTP_UNPROCESSABLE = 422


def _base_url() -> str:
    """服务地址（去掉末尾斜杠，避免拼出双斜杠）。

    :return: 形如 http://127.0.0.1:8000 的地址
    """
    return os.getenv("RAG_BASE_URL", DEFAULT_BASE_URL).rstrip("/")


def _api_key() -> str:
    """与服务端一致的 X-API-Key。

    :return: 环境变量 RAG_API_KEY，未配置时取本地示例值
    """
    return os.getenv("RAG_API_KEY", DEFAULT_API_KEY)


def _request(
    path: str,
    *,
    method: str = "GET",
    payload: dict | None = None,
    api_key: str | None = None,
    timeout: float = DEFAULT_TIMEOUT_SECONDS,
) -> tuple[int, dict]:
    """发送一次 HTTP 请求并解析 JSON 响应。

    :param path: 以 / 开头的接口路径
    :param method: HTTP 方法
    :param payload: 请求体（None 表示不带请求体）
    :param api_key: 需要携带的 X-API-Key（None 表示不携带）
    :param timeout: 超时时间（秒）
    :return: (HTTP 状态码, 解析后的响应体)；响应体非 JSON 时返回空字典
    :raises RuntimeError: 网络不可达等非 HTTP 错误
    """
    url = f"{_base_url()}{path}"
    data = None
    headers = {"Accept": "application/json"}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if api_key is not None:
        headers["X-API-Key"] = api_key

    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.status, _parse_body(response.read())
    except urllib.error.HTTPError as exc:
        # 4xx / 5xx 同样需要读取响应体，断言才能覆盖错误结构
        return exc.code, _parse_body(exc.read())
    except urllib.error.URLError as exc:
        raise RuntimeError(f"无法连接服务 {url}：{exc.reason}（请先启动 rag-service）") from exc


def _parse_body(raw: bytes) -> dict:
    """把响应体解析为字典。

    :param raw: 原始字节
    :return: 解析结果；空响应或非 JSON 返回空字典
    """
    if not raw:
        return {}
    try:
        return json.loads(raw.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError):
        logger.warning("响应体不是合法 JSON，长度 %s 字节", len(raw))
        return {}


class SmokeTest(unittest.TestCase):
    """服务启动后可独立执行的基础可用性检查。"""

    def test_health_should_be_ok(self) -> None:
        """探活接口应返回 200 且 status=ok。"""
        status_code, body = _request("/health")

        self.assertEqual(status_code, HTTP_OK, f"预期 200，实际 {status_code}：{body}")
        self.assertEqual(body.get("status"), "ok")
        self.assertIn("app", body)

    def test_ask_should_require_api_key(self) -> None:
        """未携带 X-API-Key 的问答请求应被拒绝。"""
        status_code, body = _request(
            "/api/knowledge/ask",
            method="POST",
            payload={"question": "幼猫多久驱虫一次？", "enabledDocIds": [1]},
        )

        self.assertEqual(status_code, HTTP_UNAUTHORIZED, f"预期 401，实际 {status_code}：{body}")
        self.assertEqual(body.get("code"), HTTP_UNAUTHORIZED)

    def test_ask_should_reject_blank_question(self) -> None:
        """问题为空时应返回参数校验错误。"""
        status_code, body = _request(
            "/api/knowledge/ask",
            method="POST",
            payload={"question": "", "enabledDocIds": [1]},
            api_key=_api_key(),
        )

        self.assertEqual(status_code, HTTP_UNPROCESSABLE, f"预期 422，实际 {status_code}：{body}")
        self.assertEqual(body.get("code"), HTTP_UNPROCESSABLE)

    @unittest.skipUnless(os.getenv("RAG_SMOKE_ASK") == "1", "设置 RAG_SMOKE_ASK=1 才会调用真实模型")
    def test_ask_should_return_answer(self) -> None:
        """完整问答链路（需 Ollama 可用且索引已建立）。"""
        status_code, body = _request(
            "/api/knowledge/ask",
            method="POST",
            payload={"question": "幼猫多久驱虫一次？", "enabledDocIds": [1]},
            api_key=_api_key(),
            timeout=ASK_TIMEOUT_SECONDS,
        )

        self.assertEqual(status_code, HTTP_OK, f"预期 200，实际 {status_code}：{body}")
        self.assertTrue(body.get("answer"), "answer 不应为空")
        self.assertIsInstance(body.get("references"), list)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s")
    unittest.main(verbosity=2)
