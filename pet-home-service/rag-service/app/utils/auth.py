"""X-API-Key 鉴权。"""

import logging
import secrets

from fastapi import Header, HTTPException, status

from app.config.settings import get_settings

logger = logging.getLogger(__name__)

# 鉴权请求头名称（按约定的固定值，FastAPI 请求头名称本身不区分大小写）
API_KEY_HEADER = "X-API-Key"


async def verify_api_key(x_api_key: str | None = Header(default=None, alias=API_KEY_HEADER)) -> None:
    """校验请求头中的 X-API-Key。

    :param x_api_key: 请求头 X-API-Key 的值
    :raises HTTPException: 缺失或不匹配时返回 401
    """
    expected = get_settings().rag_api_key
    # 使用常量时间比较，避免通过响应耗时被逐字节猜出 Key
    if not x_api_key or not secrets.compare_digest(x_api_key, expected):
        logger.warning("API Key 校验失败：%s", "缺失" if not x_api_key else "不匹配")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="非法的 API Key",
            headers={"WWW-Authenticate": API_KEY_HEADER},
        )
