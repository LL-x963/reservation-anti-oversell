package com.pethome.rag;

import com.pethome.common.exception.KnowledgeServiceException;
import com.pethome.entity.KnowledgeDoc;
import com.pethome.rag.dto.RagReindexResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG 索引客户端 - 真实实现（调用 rag-service 的重建索引接口）
 *
 * <p>替换了原先的空实现：管理端触发重建索引时，会把全量启用文档推给 RAG 服务重建向量库，
 * 调用失败返回 false，由 {@code KnowledgeIndexRebuildService} 把文档索引状态置为 3失败。</p>
 *
 * @author pet-home
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestRagIndexClient implements RagIndexClient {

    private final KnowledgeClient knowledgeClient;

    @Override
    public boolean rebuildIndex(List<KnowledgeDoc> docs) {
        try {
            RagReindexResponse response = knowledgeClient.rebuildIndex(docs);
            log.info("RAG 索引重建成功: documentCount={}, chunkCount={}",
                    response.getDocumentCount(), response.getChunkCount());
            return true;
        } catch (KnowledgeServiceException e) {
            // 不抛出，交给调用方把参与重建的文档索引状态置为 3失败，便于管理端重试
            log.warn("RAG 索引重建失败: docs={}", docs == null ? 0 : docs.size(), e);
            return false;
        }
    }

}
