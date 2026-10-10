"""HTTP 请求 / 响应数据模型。

对外字段保持与接口约定一致的 camelCase（通过 alias 映射），Python 内部使用 snake_case。
"""

from pydantic import BaseModel, ConfigDict, Field


class ChatMessage(BaseModel):
    """一轮对话历史。"""

    role: str = Field(default="user", description="角色：user / assistant")
    content: str = Field(default="", description="消息内容")


class PetInfo(BaseModel):
    """宠物信息，用于让回答更贴合宠物实际情况。"""

    # 允许前端透传额外字段（如 name、gender），会在 Prompt 中一并拼接
    model_config = ConfigDict(extra="allow")

    breed: str | None = Field(default=None, description="品种")
    age: float | None = Field(default=None, description="年龄（岁）")
    weight: float | None = Field(default=None, description="体重（kg）")


class AskRequest(BaseModel):
    """POST /api/knowledge/ask 请求体。"""

    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    question: str = Field(..., min_length=1, max_length=1000, description="用户问题")
    user_id: int | None = Field(default=None, alias="userId", description="用户 ID（仅用于日志定位）")
    conversation_history: list[ChatMessage] = Field(
        default_factory=list, alias="conversationHistory", description="历史对话（仅取最近 N 轮）"
    )
    pet_info: PetInfo | None = Field(default=None, alias="petInfo", description="宠物信息")
    enabled_doc_ids: list[int] = Field(
        default_factory=list,
        alias="enabledDocIds",
        description="允许检索的知识库文档 ID；为空表示没有可用知识片段",
    )


class Reference(BaseModel):
    """参考来源片段。"""

    model_config = ConfigDict(populate_by_name=True)

    doc_id: int = Field(..., alias="docId", description="来源文档 ID")
    title: str = Field(default="", description="来源文档标题")
    content: str = Field(default="", description="命中的片段内容")


class AskResponse(BaseModel):
    """POST /api/knowledge/ask 响应体。"""

    answer: str = Field(..., description="回答内容（末尾已拼接免责声明）")
    references: list[Reference] = Field(default_factory=list, description="参考来源")


class DocumentItem(BaseModel):
    """待入库的知识库文档。"""

    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    id: int = Field(..., description="文档 ID")
    title: str = Field(default="", description="文档标题")
    category: str | None = Field(default=None, description="文档分类")
    content: str = Field(..., min_length=1, description="文档正文")


class ReindexRequest(BaseModel):
    """POST /api/knowledge/reindex 请求体。"""

    documents: list[DocumentItem] = Field(
        default_factory=list, max_length=2000, description="全量启用文档列表（清空后重建）"
    )


class ReindexResponse(BaseModel):
    """POST /api/knowledge/reindex 响应体。"""

    model_config = ConfigDict(populate_by_name=True)

    document_count: int = Field(..., alias="documentCount", description="入库文档数")
    chunk_count: int = Field(..., alias="chunkCount", description="生成的知识片段数")


class ErrorResponse(BaseModel):
    """统一错误响应体。"""

    code: int = Field(..., description="HTTP 状态码")
    message: str = Field(..., description="错误提示（不含内部堆栈）")
