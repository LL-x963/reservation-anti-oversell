package com.pethome.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pethome.dto.KnowledgeDocAddRequest;
import com.pethome.dto.KnowledgeDocPageQuery;
import com.pethome.dto.KnowledgeDocUpdateRequest;
import com.pethome.entity.KnowledgeDoc;
import com.pethome.vo.KnowledgeDocDetailVO;
import com.pethome.vo.KnowledgeDocVO;
import com.pethome.vo.KnowledgeIndexStatusVO;

/**
 * 知识库文档服务
 *
 * @author pet-home
 */
public interface KnowledgeDocService extends IService<KnowledgeDoc> {

    /**
     * 分页查询（支持按分类、启用状态筛选，不含正文）
     */
    IPage<KnowledgeDocVO> page(KnowledgeDocPageQuery query);

    /**
     * 文档详情（含正文）
     */
    KnowledgeDocDetailVO detail(Long id);

    /**
     * 新增文档，标题全局唯一
     *
     * @return 新增文档 id
     */
    Long add(KnowledgeDocAddRequest request);

    /**
     * 修改文档（标题/分类/内容任一变更时，索引状态重置为 0未索引）
     */
    void update(KnowledgeDocUpdateRequest request);

    /**
     * 启用/禁用文档
     *
     * @param enabled 1启用 0禁用
     */
    void updateStatus(Long id, Integer enabled);

    /**
     * 删除文档
     */
    void delete(Long id);

    /**
     * 索引状态概览
     */
    KnowledgeIndexStatusVO indexStatus();

    /**
     * 重建索引
     *
     * <p>将所有 enabled=1 的文档置为 1索引中，随后异步调用 RAG 服务重建索引，
     * 成功置为 2正常、失败置为 3失败；不阻塞主线程。</p>
     */
    void rebuildIndex();

}
