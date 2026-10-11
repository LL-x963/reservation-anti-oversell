package com.pethome.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pethome.common.enums.IndexStatus;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.KnowledgeDocAddRequest;
import com.pethome.dto.KnowledgeDocPageQuery;
import com.pethome.dto.KnowledgeDocUpdateRequest;
import com.pethome.entity.KnowledgeDoc;
import com.pethome.vo.KnowledgeDocDetailVO;
import com.pethome.vo.KnowledgeDocVO;
import com.pethome.vo.KnowledgeIndexStatusVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 知识库文档服务层测试（覆盖业务规则，事务回滚不污染数据库）
 *
 * @author pet-home
 */
@SpringBootTest
@Transactional
class KnowledgeDocServiceTest {

    private static final String TITLE = "IT-知识库-猫咪护理";
    private static final String TITLE_2 = "IT-知识库-狗狗饮食";
    private static final String CONTENT = "猫咪日常护理要点：定期梳毛、按时驱虫。";
    private static final String CATEGORY = "护理";

    @Autowired
    private KnowledgeDocService knowledgeDocService;

    // ==================== 新增 ====================

    @Test
    void addShouldFillDefaultsAndAuditFields() {
        Long id = addDoc("   " + TITLE + "   ", CATEGORY, CONTENT);

        KnowledgeDoc doc = knowledgeDocService.getById(id);
        assertThat(doc).isNotNull();
        // 标题去空格
        assertThat(doc.getTitle()).isEqualTo(TITLE);
        assertThat(doc.getCategory()).isEqualTo(CATEGORY);
        assertThat(doc.getContent()).isEqualTo(CONTENT);
        // 默认启用、默认未索引
        assertThat(doc.getEnabled()).isEqualTo(1);
        assertThat(doc.getIndexStatus()).isEqualTo(IndexStatus.NOT_INDEXED.getCode());
        assertThat(doc.getCreateTime()).isNotNull();
        assertThat(doc.getUpdateTime()).isNotNull();
    }

    @Test
    void addShouldRejectDuplicatedTitle() {
        addDoc(TITLE, CATEGORY, CONTENT);

        assertThatThrownBy(() -> addDoc(TITLE, "饮食", "另一份内容"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档标题已存在");
    }

    @Test
    void addShouldRejectInvalidCategory() {
        assertThatThrownBy(() -> addDoc(TITLE, "其他分类", CONTENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档分类不合法");
    }

    @Test
    void addShouldAcceptAllFixedCategories() {
        List<String> categories = List.of("护理", "饮食", "疾病预防");

        for (String category : categories) {
            Long id = addDoc(TITLE + "-" + category, category, CONTENT);
            assertThat(knowledgeDocService.getById(id).getCategory()).isEqualTo(category);
        }
    }

    // ==================== 修改 ====================

    @Test
    void updateShouldResetIndexStatusWhenContentChanged() {
        Long id = addDoc(TITLE, CATEGORY, CONTENT);
        setIndexStatus(id, IndexStatus.NORMAL.getCode());

        // 仅正文变化
        updateDoc(id, TITLE, CATEGORY, CONTENT + "补充说明");

        assertThat(knowledgeDocService.getById(id).getIndexStatus())
                .isEqualTo(IndexStatus.NOT_INDEXED.getCode());
    }

    @Test
    void updateShouldResetIndexStatusWhenTitleOrCategoryChanged() {
        Long id = addDoc(TITLE, CATEGORY, CONTENT);
        setIndexStatus(id, IndexStatus.NORMAL.getCode());
        updateDoc(id, TITLE + "改", CATEGORY, CONTENT);
        assertThat(knowledgeDocService.getById(id).getIndexStatus()).isZero();

        setIndexStatus(id, IndexStatus.NORMAL.getCode());
        updateDoc(id, TITLE + "改", "饮食", CONTENT);
        assertThat(knowledgeDocService.getById(id).getIndexStatus()).isZero();
    }

    @Test
    void updateShouldKeepIndexStatusWhenContentUnchanged() {
        Long id = addDoc(TITLE, CATEGORY, CONTENT);
        setIndexStatus(id, IndexStatus.NORMAL.getCode());

        // 标题/分类/内容完全一致，索引未失效
        updateDoc(id, "  " + TITLE + "  ", CATEGORY, CONTENT);

        assertThat(knowledgeDocService.getById(id).getIndexStatus())
                .isEqualTo(IndexStatus.NORMAL.getCode());
    }

    @Test
    void updateShouldRejectDuplicatedTitle() {
        addDoc(TITLE, CATEGORY, CONTENT);
        Long id = addDoc(TITLE_2, CATEGORY, CONTENT);

        assertThatThrownBy(() -> updateDoc(id, TITLE, CATEGORY, CONTENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档标题已存在");
    }

    @Test
    void updateShouldAllowKeepingOwnTitle() {
        Long id = addDoc(TITLE, CATEGORY, CONTENT);

        updateDoc(id, TITLE, CATEGORY, "新内容");

        assertThat(knowledgeDocService.getById(id).getContent()).isEqualTo("新内容");
    }

    @Test
    void updateShouldRejectNonexistentDoc() {
        assertThatThrownBy(() -> updateDoc(99999999L, TITLE, CATEGORY, CONTENT))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档不存在");
    }

    // ==================== 启用/禁用 ====================

    @Test
    void updateStatusShouldToggleEnabledAndKeepIndexStatus() {
        Long id = addDoc(TITLE, CATEGORY, CONTENT);
        setIndexStatus(id, IndexStatus.NORMAL.getCode());

        knowledgeDocService.updateStatus(id, 0);
        KnowledgeDoc disabled = knowledgeDocService.getById(id);
        assertThat(disabled.getEnabled()).isZero();
        // 启用/禁用不重置索引状态
        assertThat(disabled.getIndexStatus()).isEqualTo(IndexStatus.NORMAL.getCode());

        knowledgeDocService.updateStatus(id, 1);
        assertThat(knowledgeDocService.getById(id).getEnabled()).isEqualTo(1);
    }

    @Test
    void updateStatusShouldRejectNonexistentDoc() {
        assertThatThrownBy(() -> knowledgeDocService.updateStatus(99999999L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档不存在");
    }

    // ==================== 删除 ====================

    @Test
    void deleteShouldRemoveDoc() {
        Long id = addDoc(TITLE, CATEGORY, CONTENT);

        knowledgeDocService.delete(id);

        assertThat(knowledgeDocService.getById(id)).isNull();
    }

    @Test
    void deleteShouldRejectNonexistentDoc() {
        assertThatThrownBy(() -> knowledgeDocService.delete(99999999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档不存在");
    }

    // ==================== 查询 ====================

    @Test
    void pageShouldFilterByCategoryAndEnabled() {
        Long enabledId = addDoc(TITLE, CATEGORY, CONTENT);
        Long disabledId = addDoc(TITLE_2, "饮食", CONTENT);
        knowledgeDocService.updateStatus(disabledId, 0);

        // 分类筛选（开发库可能已有真实文档，这里断言本次新增文档被筛出即可）
        IPage<KnowledgeDocVO> byCategory = knowledgeDocService.page(query(1, 100, CATEGORY, null));
        assertThat(byCategory.getRecords()).extracting(KnowledgeDocVO::getId).contains(enabledId);

        // 启用状态筛选
        assertThat(knowledgeDocService.page(query(1, 100, null, 1)).getRecords())
                .extracting(KnowledgeDocVO::getId).contains(enabledId);
        IPage<KnowledgeDocVO> disabledPage = knowledgeDocService.page(query(1, 100, null, 0));
        assertThat(disabledPage.getRecords()).extracting(KnowledgeDocVO::getId).contains(disabledId);

        // 索引状态描述
        KnowledgeDocVO vo = disabledPage.getRecords().stream()
                .filter(record -> disabledId.equals(record.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(vo.getIndexStatus()).isEqualTo(IndexStatus.NOT_INDEXED.getCode());
        assertThat(vo.getIndexStatusDesc()).isEqualTo("未索引");
    }

    @Test
    void detailShouldReturnContentAndStatusDesc() {
        Long id = addDoc(TITLE, CATEGORY, CONTENT);

        KnowledgeDocDetailVO detail = knowledgeDocService.detail(id);

        assertThat(detail.getTitle()).isEqualTo(TITLE);
        assertThat(detail.getCategory()).isEqualTo(CATEGORY);
        assertThat(detail.getContent()).isEqualTo(CONTENT);
        assertThat(detail.getEnabled()).isEqualTo(1);
        assertThat(detail.getIndexStatusDesc()).isEqualTo("未索引");
    }

    @Test
    void detailShouldRejectNonexistentDoc() {
        assertThatThrownBy(() -> knowledgeDocService.detail(99999999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文档不存在");
    }

    @Test
    void indexStatusShouldAggregateCountsByStatus() {
        KnowledgeIndexStatusVO before = knowledgeDocService.indexStatus();

        Long normalId = addDoc(TITLE, CATEGORY, CONTENT);
        setIndexStatus(normalId, IndexStatus.NORMAL.getCode());
        addDoc(TITLE_2, "饮食", CONTENT);
        Long disabledId = addDoc(TITLE + "-禁用", "疾病预防", CONTENT);
        knowledgeDocService.updateStatus(disabledId, 0);

        KnowledgeIndexStatusVO after = knowledgeDocService.indexStatus();

        assertThat(after.getTotalCount() - before.getTotalCount()).isEqualTo(3);
        assertThat(after.getEnabledCount() - before.getEnabledCount()).isEqualTo(2);
        assertThat(after.getDisabledCount() - before.getDisabledCount()).isEqualTo(1);
        assertThat(after.getNormalCount() - before.getNormalCount()).isEqualTo(1);
        assertThat(after.getNotIndexedCount() - before.getNotIndexedCount()).isEqualTo(2);
        assertThat(after.getFailedCount() - before.getFailedCount()).isZero();
        assertThat(after.getIndexingCount()).isZero();
        assertThat(after.getRebuilding()).isFalse();
    }

    // ==================== 重建索引（同步部分） ====================

    @Test
    void rebuildIndexShouldRejectWhenAlreadyIndexing() {
        Long id = addDoc(TITLE, CATEGORY, CONTENT);
        setIndexStatus(id, IndexStatus.INDEXING.getCode());

        assertThatThrownBy(() -> knowledgeDocService.rebuildIndex())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正在重建中");
    }

    @Test
    void rebuildIndexShouldNotTouchDisabledDocs() {
        Long id = addDoc(TITLE, CATEGORY, CONTENT);
        knowledgeDocService.updateStatus(id, 0);

        // 禁用文档不参与重建，也不会被置为索引中
        knowledgeDocService.rebuildIndex();

        assertThat(knowledgeDocService.getById(id).getIndexStatus())
                .isEqualTo(IndexStatus.NOT_INDEXED.getCode());
    }

    // ==================== 辅助方法 ====================

    private Long addDoc(String title, String category, String content) {
        KnowledgeDocAddRequest request = new KnowledgeDocAddRequest();
        request.setTitle(title);
        request.setCategory(category);
        request.setContent(content);
        return knowledgeDocService.add(request);
    }

    private void updateDoc(Long id, String title, String category, String content) {
        KnowledgeDocUpdateRequest request = new KnowledgeDocUpdateRequest();
        request.setId(id);
        request.setTitle(title);
        request.setCategory(category);
        request.setContent(content);
        knowledgeDocService.update(request);
    }

    private KnowledgeDocPageQuery query(Integer page, Integer pageSize, String category, Integer enabled) {
        KnowledgeDocPageQuery query = new KnowledgeDocPageQuery();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setCategory(category);
        query.setEnabled(enabled);
        return query;
    }

    /**
     * 直接改写索引状态，模拟“已索引”“索引中”等场景
     */
    private void setIndexStatus(Long id, Integer indexStatus) {
        KnowledgeDoc update = new KnowledgeDoc();
        update.setId(id);
        update.setIndexStatus(indexStatus);
        knowledgeDocService.updateById(update);
    }

}
