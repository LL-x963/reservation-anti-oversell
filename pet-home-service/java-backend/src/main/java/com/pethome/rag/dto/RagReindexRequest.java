package com.pethome.rag.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 服务重建索引请求体，对应 rag-service 的 POST /api/knowledge/reindex
 *
 * @author pet-home
 */
@Data
public class RagReindexRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 全量启用文档列表（RAG 侧会清空后重建） */
    private List<RagReindexDocument> documents = new ArrayList<>();

}
