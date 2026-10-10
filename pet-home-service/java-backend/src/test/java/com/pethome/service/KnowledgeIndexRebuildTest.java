package com.pethome.service;

import com.pethome.common.enums.IndexStatus;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.KnowledgeDocAddRequest;
import com.pethome.vo.KnowledgeIndexStatusVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 知识库索引重建（异步链路）测试
 *
 * <p>异步线程与测试线程不在同一事务中，故本类不使用 @Transactional，
 * 改为在 @AfterEach 中按标题前缀清理数据。</p>
 *
 * @author pet-home
 */
@SpringBootTest
class KnowledgeIndexRebuildTest {

    private static final String TITLE_PREFIX = "IT-重建索引-";
    private static final String CATEGORY = "护理";

    @Autowired
    private KnowledgeDocService knowledgeDocService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM knowledge_doc WHERE title LIKE ?", TITLE_PREFIX + "%");
    }

    @Test
    void rebuildIndexShouldMarkEnabledDocsNormalAndSkipDisabled() {
        Long enabledId1 = addDoc(TITLE_PREFIX + "启用1");
        Long enabledId2 = addDoc(TITLE_PREFIX + "启用2");
        Long disabledId = addDoc(TITLE_PREFIX + "禁用");
        knowledgeDocService.updateStatus(disabledId, 0);

        assertThat(knowledgeDocService.indexStatus().getRebuilding()).isFalse();

        knowledgeDocService.rebuildIndex();

        KnowledgeIndexStatusVO after = awaitRebuildFinished();
        assertThat(after.getRebuilding()).isFalse();

        // 启用文档重建成功（RAG 为空实现，按成功处理 => 2正常）
        assertThat(knowledgeDocService.getById(enabledId1).getIndexStatus())
                .isEqualTo(IndexStatus.NORMAL.getCode());
        assertThat(knowledgeDocService.getById(enabledId2).getIndexStatus())
                .isEqualTo(IndexStatus.NORMAL.getCode());
        // 禁用文档不参与索引，状态保持未索引
        assertThat(knowledgeDocService.getById(disabledId).getIndexStatus())
                .isEqualTo(IndexStatus.NOT_INDEXED.getCode());
    }

    @Test
    void rebuildIndexShouldRejectWhenIndexingExists() {
        Long id = addDoc(TITLE_PREFIX + "索引中");
        jdbcTemplate.update("UPDATE knowledge_doc SET index_status = ? WHERE id = ?",
                IndexStatus.INDEXING.getCode(), id);

        assertThatThrownBy(() -> knowledgeDocService.rebuildIndex())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正在重建中");
    }

    @Test
    void rebuildIndexShouldNotBlockWhenNoEnabledDoc() {
        Long id = addDoc(TITLE_PREFIX + "无启用文档");
        knowledgeDocService.updateStatus(id, 0);

        // 没有启用文档时直接跳过，不抛异常
        knowledgeDocService.rebuildIndex();

        assertThat(awaitRebuildFinished().getRebuilding()).isFalse();
        // 禁用文档不会被重建流程改写状态
        assertThat(knowledgeDocService.getById(id).getIndexStatus())
                .isEqualTo(IndexStatus.NOT_INDEXED.getCode());
    }

    /**
     * 轮询等待异步重建结束（最长 15 秒）
     */
    private KnowledgeIndexStatusVO awaitRebuildFinished() {
        long deadline = System.currentTimeMillis() + 15_000L;
        KnowledgeIndexStatusVO status = knowledgeDocService.indexStatus();
        while (Boolean.TRUE.equals(status.getRebuilding()) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待索引重建被中断", e);
            }
            status = knowledgeDocService.indexStatus();
        }
        return status;
    }

    private Long addDoc(String title) {
        KnowledgeDocAddRequest request = new KnowledgeDocAddRequest();
        request.setTitle(title);
        request.setCategory(CATEGORY);
        request.setContent("内容-" + title);
        return knowledgeDocService.add(request);
    }

}
