package com.pethome.rag.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 服务问答请求体，对应 rag-service 的 POST /api/knowledge/ask
 *
 * @author pet-home
 */
@Data
public class RagAskRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户问题 */
    private String question;

    /** 用户 id（RAG 侧仅用于日志定位） */
    private Long userId;

    /** 对话历史（最多最近 3 轮，1 轮 = 一问一答） */
    private List<RagChatMessage> conversationHistory = new ArrayList<>();

    /** 宠物信息（未传 petId 时为空） */
    private RagPetInfo petInfo;

    /** 允许检索的知识库文档 id（enabled=1 且 index_status=2） */
    private List<Long> enabledDocIds = new ArrayList<>();

}
