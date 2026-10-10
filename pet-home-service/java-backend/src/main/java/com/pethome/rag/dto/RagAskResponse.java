package com.pethome.rag.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 服务问答响应体
 *
 * @author pet-home
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RagAskResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回答内容（末尾已由 RAG 侧拼好免责声明） */
    private String answer;

    /** 参考来源片段 */
    private List<RagReference> references = new ArrayList<>();

}
