package com.pethome.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pethome.common.enums.IndexStatus;
import com.pethome.entity.KnowledgeDoc;
import com.pethome.mapper.KnowledgeDocMapper;
import com.pethome.rag.RagIndexClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 知识库索引重建服务（异步执行）
 *
 * <p>独立成 Bean 是为了让 {@link Async} 生效（同类内部调用不会走代理）。
 * 这里直接依赖 {@link KnowledgeDocMapper} 而非 KnowledgeDocService，避免与 KnowledgeDocServiceImpl 形成循环依赖。</p>
 *
 * @author pet-home
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndexRebuildService {

    private final RagIndexClient ragIndexClient;
    private final KnowledgeDocMapper knowledgeDocMapper;

    /**
     * 异步调用 RAG 服务重建索引，并回写索引状态
     *
     * @param ids  参与重建的文档 id 列表
     * @param docs 参与重建的文档（enabled = 1）
     */
    @Async
    public void rebuildAsync(List<Long> ids, List<KnowledgeDoc> docs) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        try {
            boolean success = ragIndexClient.rebuildIndex(docs);
            if (success) {
                updateIndexStatus(ids, IndexStatus.NORMAL);
                log.info("知识库索引重建成功: docs={}", ids.size());
            } else {
                updateIndexStatus(ids, IndexStatus.FAILED);
                log.warn("知识库索引重建失败: docs={}", ids.size());
            }
        } catch (Exception e) {
            // 重建失败不影响主流程，仅记录日志并把状态置为失败，便于管理端重试
            log.error("知识库索引重建异常: docs={}", ids.size(), e);
            updateIndexStatus(ids, IndexStatus.FAILED);
        }
    }

    private void updateIndexStatus(List<Long> ids, IndexStatus status) {
        knowledgeDocMapper.update(null, Wrappers.<KnowledgeDoc>lambdaUpdate()
                .in(KnowledgeDoc::getId, ids)
                .set(KnowledgeDoc::getIndexStatus, status.getCode()));
    }

}
