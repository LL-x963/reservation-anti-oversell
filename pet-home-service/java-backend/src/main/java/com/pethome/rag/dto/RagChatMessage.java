package com.pethome.rag.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * RAG 对话历史消息
 *
 * @author pet-home
 */
@Data
public class RagChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色：user / assistant */
    private String role;

    /** 消息内容 */
    private String content;

}
