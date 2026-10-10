package com.pethome.rag.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * RAG 服务重建索引响应体
 *
 * @author pet-home
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RagReindexResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 入库文档数 */
    private Integer documentCount;

    /** 生成的向量片段数 */
    private Integer chunkCount;

}
