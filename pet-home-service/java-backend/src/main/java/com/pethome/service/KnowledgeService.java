package com.pethome.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.pethome.dto.AiConversationPageQuery;
import com.pethome.dto.KnowledgeAskRequest;
import com.pethome.entity.AiConversation;
import com.pethome.vo.AiConversationVO;
import com.pethome.vo.KnowledgeAskVO;

/**
 * 知识库问答服务（用户端）
 *
 * <p>负责调用 RAG 服务并把问答结果落库到 ai_conversation，所有历史数据均限定在当前登录用户范围内。</p>
 *
 * @author pet-home
 */
public interface KnowledgeService extends IService<AiConversation> {

    /**
     * 提问：组装上下文调用 RAG 服务，成功后记录问答历史
     *
     * @throws com.pethome.common.exception.KnowledgeServiceException RAG 服务不可用/超时
     */
    KnowledgeAskVO ask(KnowledgeAskRequest request);

    /**
     * 分页查询当前登录用户的问答历史（按提问时间倒序）
     */
    IPage<AiConversationVO> pageHistory(AiConversationPageQuery query);

    /**
     * 删除当前登录用户的单条问答历史
     */
    void deleteHistory(Long id);

    /**
     * 清空当前登录用户的全部问答历史
     */
    void clearHistory();

}
