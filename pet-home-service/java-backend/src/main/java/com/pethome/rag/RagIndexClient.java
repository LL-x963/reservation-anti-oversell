package com.pethome.rag;

import com.pethome.entity.KnowledgeDoc;

import java.util.List;

/**
 * RAG 服务索引客户端
 *
 * <p>知识库重建索引时，由本接口负责把文档推送给 RAG 服务（向量库）完成索引重建。</p>
 *
 * @author pet-home
 */
public interface RagIndexClient {

    /**
     * 重建索引
     *
     * @param docs 需要参与索引的文档（enabled = 1）
     * @return true 重建成功；false 重建失败（调用方会把文档索引状态置为 3失败）
     */
    boolean rebuildIndex(List<KnowledgeDoc> docs);

}
