package com.pethome.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.enums.IndexStatus;
import com.pethome.common.enums.KnowledgeCategory;
import com.pethome.common.exception.BusinessException;
import com.pethome.dto.KnowledgeDocAddRequest;
import com.pethome.dto.KnowledgeDocPageQuery;
import com.pethome.dto.KnowledgeDocUpdateRequest;
import com.pethome.entity.KnowledgeDoc;
import com.pethome.mapper.KnowledgeDocMapper;
import com.pethome.service.KnowledgeDocService;
import com.pethome.service.KnowledgeIndexRebuildService;
import com.pethome.vo.KnowledgeDocDetailVO;
import com.pethome.vo.KnowledgeDocVO;
import com.pethome.vo.KnowledgeIndexStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;

/**
 * 知识库文档服务实现
 *
 * @author pet-home
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocServiceImpl extends ServiceImpl<KnowledgeDocMapper, KnowledgeDoc>
        implements KnowledgeDocService {

    /** 启用 */
    private static final int ENABLED = 1;
    /** 禁用 */
    private static final int DISABLED = 0;

    private final KnowledgeIndexRebuildService knowledgeIndexRebuildService;

    @Override
    public IPage<KnowledgeDocVO> page(KnowledgeDocPageQuery query) {
        LambdaQueryWrapper<KnowledgeDoc> wrapper = new LambdaQueryWrapper<KnowledgeDoc>()
                .eq(query.getCategory() != null && !query.getCategory().isBlank(),
                        KnowledgeDoc::getCategory, query.getCategory())
                .eq(query.getEnabled() != null, KnowledgeDoc::getEnabled, query.getEnabled())
                .orderByDesc(KnowledgeDoc::getId);

        Page<KnowledgeDoc> page = page(new Page<>(query.getPage(), query.getPageSize()), wrapper);
        return page.convert(this::toVO);
    }

    @Override
    public KnowledgeDocDetailVO detail(Long id) {
        KnowledgeDoc doc = getById(id);
        if (doc == null) {
            throw new BusinessException("文档不存在");
        }
        KnowledgeDocDetailVO vo = new KnowledgeDocDetailVO();
        BeanUtils.copyProperties(doc, vo);
        vo.setIndexStatusDesc(IndexStatus.descOf(doc.getIndexStatus()));
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long add(KnowledgeDocAddRequest request) {
        String title = request.getTitle().trim();
        // 分类固定三类，非法取值直接拒绝
        String category = KnowledgeCategory.of(request.getCategory()).getValue();
        checkTitleUnique(title, null);

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setTitle(title);
        doc.setCategory(category);
        doc.setContent(request.getContent());
        // 新增文档默认启用，且尚未建立索引
        doc.setEnabled(ENABLED);
        doc.setIndexStatus(IndexStatus.NOT_INDEXED.getCode());
        save(doc);

        log.info("新增知识库文档成功: id={}, title={}, category={}", doc.getId(), title, category);
        return doc.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(KnowledgeDocUpdateRequest request) {
        KnowledgeDoc exists = getById(request.getId());
        if (exists == null) {
            throw new BusinessException("文档不存在");
        }
        String title = request.getTitle().trim();
        String category = KnowledgeCategory.of(request.getCategory()).getValue();
        checkTitleUnique(title, request.getId());

        KnowledgeDoc update = new KnowledgeDoc();
        update.setId(request.getId());
        update.setTitle(title);
        update.setCategory(category);
        update.setContent(request.getContent());

        // 标题/分类/内容任一变更，原索引已失效，重置为 0未索引等待重建；仅启用状态变更不受影响
        boolean contentChanged = !Objects.equals(exists.getTitle(), title)
                || !Objects.equals(exists.getCategory(), category)
                || !Objects.equals(exists.getContent(), request.getContent());
        if (contentChanged) {
            update.setIndexStatus(IndexStatus.NOT_INDEXED.getCode());
        }
        updateById(update);

        log.info("修改知识库文档成功: id={}, title={}, indexReset={}", request.getId(), title, contentChanged);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer enabled) {
        if (getById(id) == null) {
            throw new BusinessException("文档不存在");
        }

        KnowledgeDoc update = new KnowledgeDoc();
        update.setId(id);
        update.setEnabled(enabled);
        // 启用/禁用不重置索引状态：禁用文档在重建索引时被排除，从而不参与检索
        updateById(update);

        log.info("知识库文档启用状态变更成功: id={}, enabled={}", id, enabled);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("文档不存在");
        }
        removeById(id);
        log.info("删除知识库文档成功: id={}", id);
    }

    @Override
    public KnowledgeIndexStatusVO indexStatus() {
        long indexing = countByIndexStatus(IndexStatus.INDEXING.getCode());
        long notIndexed = countByIndexStatus(IndexStatus.NOT_INDEXED.getCode());
        long normal = countByIndexStatus(IndexStatus.NORMAL.getCode());
        long failed = countByIndexStatus(IndexStatus.FAILED.getCode());

        KnowledgeIndexStatusVO vo = new KnowledgeIndexStatusVO();
        vo.setTotalCount(count());
        vo.setEnabledCount(countByEnabled(ENABLED));
        vo.setDisabledCount(countByEnabled(DISABLED));
        vo.setNotIndexedCount(notIndexed);
        vo.setIndexingCount(indexing);
        vo.setNormalCount(normal);
        vo.setFailedCount(failed);
        vo.setRebuilding(indexing > 0);
        return vo;
    }

    @Override
    public void rebuildIndex() {
        // 防重：存在“索引中”的文档说明上一次重建尚未结束
        if (countByIndexStatus(IndexStatus.INDEXING.getCode()) > 0) {
            throw new BusinessException("索引正在重建中，请稍后再试");
        }

        // 禁用的文档不参与索引与检索，因此只处理 enabled=1
        List<KnowledgeDoc> docs = lambdaQuery()
                .eq(KnowledgeDoc::getEnabled, ENABLED)
                .orderByAsc(KnowledgeDoc::getId)
                .list();
        if (docs.isEmpty()) {
            log.info("没有需要重建索引的启用文档，跳过本次重建");
            return;
        }

        List<Long> ids = docs.stream().map(KnowledgeDoc::getId).toList();
        // 1. 全部启用文档置为 1索引中（先落库，管理端可通过 /indexStatus 看到进度）
        markIndexing(ids);
        // 2/3/4. 异步调用 RAG 服务重建索引，成功置 2、失败置 3，不阻塞主线程
        knowledgeIndexRebuildService.rebuildAsync(ids, docs);

        log.info("知识库索引重建已提交异步执行: docs={}", ids.size());
    }

    private void markIndexing(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        lambdaUpdate()
                .in(KnowledgeDoc::getId, ids)
                .set(KnowledgeDoc::getIndexStatus, IndexStatus.INDEXING.getCode())
                .update();
    }

    /**
     * 校验文档标题全局唯一
     *
     * @param title     标题（已 trim）
     * @param excludeId 需要排除的文档 id（修改时排除自身），新增传 null
     */
    private void checkTitleUnique(String title, Long excludeId) {
        Long count = lambdaQuery()
                .eq(KnowledgeDoc::getTitle, title)
                .ne(excludeId != null, KnowledgeDoc::getId, excludeId)
                .count();
        if (count != null && count > 0) {
            throw new BusinessException("文档标题已存在：" + title);
        }
    }

    private long countByEnabled(int enabled) {
        Long count = lambdaQuery().eq(KnowledgeDoc::getEnabled, enabled).count();
        return count == null ? 0L : count;
    }

    private long countByIndexStatus(Integer indexStatus) {
        Long count = lambdaQuery().eq(KnowledgeDoc::getIndexStatus, indexStatus).count();
        return count == null ? 0L : count;
    }

    private KnowledgeDocVO toVO(KnowledgeDoc doc) {
        KnowledgeDocVO vo = new KnowledgeDocVO();
        BeanUtils.copyProperties(doc, vo);
        vo.setIndexStatusDesc(IndexStatus.descOf(doc.getIndexStatus()));
        return vo;
    }

}
