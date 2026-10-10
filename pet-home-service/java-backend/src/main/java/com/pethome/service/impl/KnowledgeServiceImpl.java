package com.pethome.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.pethome.common.context.UserContext;
import com.pethome.common.exception.BusinessException;
import com.pethome.common.exception.KnowledgeServiceException;
import com.pethome.dto.AiConversationPageQuery;
import com.pethome.dto.ConversationMessageDTO;
import com.pethome.dto.KnowledgeAskRequest;
import com.pethome.entity.AiConversation;
import com.pethome.mapper.AiConversationMapper;
import com.pethome.rag.KnowledgeClient;
import com.pethome.rag.dto.RagAskRequest;
import com.pethome.rag.dto.RagAskResponse;
import com.pethome.rag.dto.RagChatMessage;
import com.pethome.rag.dto.RagPetInfo;
import com.pethome.rag.dto.RagReference;
import com.pethome.service.KnowledgeDocService;
import com.pethome.service.KnowledgeService;
import com.pethome.service.PetProfileService;
import com.pethome.vo.AiConversationVO;
import com.pethome.vo.KnowledgeAskVO;
import com.pethome.vo.KnowledgeReferenceVO;
import com.pethome.vo.PetProfileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库问答服务实现
 *
 * @author pet-home
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl extends ServiceImpl<AiConversationMapper, AiConversation> implements KnowledgeService {

    /** 传给模型的对话轮数（1 轮 = 一问一答） */
    private static final int HISTORY_MAX_ROUNDS = 3;

    /** 性别：1公 */
    private static final int GENDER_MALE = 1;

    /** 性别：2母 */
    private static final int GENDER_FEMALE = 2;

    private final KnowledgeClient knowledgeClient;
    private final KnowledgeDocService knowledgeDocService;
    private final PetProfileService petProfileService;

    /**
     * 提问
     *
     * <p>刻意不加事务：RAG 调用是慢操作（秒级），放在事务里会长时间占用数据库连接，
     * 落库本身是单条 insert，自动提交即可。</p>
     */
    @Override
    public KnowledgeAskVO ask(KnowledgeAskRequest request) {
        Long userId = UserContext.requireUserId();
        String question = request.getQuestion().trim();

        // 1. 组装宠物信息（带归属校验，传了别人的 petId 会被拒绝）
        RagPetInfo petInfo = buildPetInfo(request.getPetId());

        // 2. 取当前可检索的文档：enabled=1 且 index_status=2正常
        List<Long> enabledDocIds = knowledgeDocService.listIndexedDocIds();

        // 3. 调用 RAG 服务（失败/超时由 KnowledgeClient 抛 KnowledgeServiceException）
        RagAskRequest ragRequest = new RagAskRequest();
        ragRequest.setQuestion(question);
        ragRequest.setUserId(userId);
        ragRequest.setPetInfo(petInfo);
        ragRequest.setEnabledDocIds(enabledDocIds);
        ragRequest.setConversationHistory(buildConversationHistory(request.getConversationHistory()));

        RagAskResponse ragResponse = knowledgeClient.ask(ragRequest);
        if (ragResponse == null || StringUtils.isBlank(ragResponse.getAnswer())) {
            log.error("RAG 服务返回空答案: userId={}, question={}", userId, question);
            throw new KnowledgeServiceException();
        }

        List<KnowledgeReferenceVO> references = toReferences(ragResponse.getReferences());

        // 4. 调用成功后记录历史（参考来源以 JSON 存库，便于历史回显）
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setPetId(request.getPetId());
        conversation.setQuestion(question);
        conversation.setAnswer(ragResponse.getAnswer());
        conversation.setReferences(references.isEmpty() ? null : JSON.toJSONString(references));
        save(conversation);

        log.info("知识库问答成功并已记录历史: id={}, userId={}, petId={}, enabledDocs={}, references={}",
                conversation.getId(), userId, request.getPetId(), enabledDocIds.size(), references.size());

        KnowledgeAskVO vo = new KnowledgeAskVO();
        vo.setAnswer(ragResponse.getAnswer());
        vo.setReferences(references);
        return vo;
    }

    @Override
    public IPage<AiConversationVO> pageHistory(AiConversationPageQuery query) {
        Long userId = UserContext.requireUserId();
        Page<AiConversation> page = page(new Page<>(query.getPage(), query.getPageSize()),
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .orderByDesc(AiConversation::getCreateTime)
                        .orderByDesc(AiConversation::getId));
        return page.convert(this::toVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteHistory(Long id) {
        Long userId = UserContext.requireUserId();
        boolean removed = remove(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getId, id)
                .eq(AiConversation::getUserId, userId));
        if (!removed) {
            // 记录不存在或不属于当前用户统一提示，避免越权探测
            throw new BusinessException("问答记录不存在");
        }
        log.info("删除问答历史成功: id={}, userId={}", id, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearHistory() {
        Long userId = UserContext.requireUserId();
        remove(new LambdaQueryWrapper<AiConversation>().eq(AiConversation::getUserId, userId));
        log.info("清空问答历史: userId={}", userId);
    }

    /**
     * 组装宠物信息；传了 petId 时校验归属，不存在或不属于当前用户会直接抛「宠物不存在」
     */
    private RagPetInfo buildPetInfo(Long petId) {
        if (petId == null) {
            return null;
        }
        PetProfileVO pet = petProfileService.detailForCurrentUser(petId);

        RagPetInfo petInfo = new RagPetInfo();
        petInfo.setName(pet.getName());
        petInfo.setBreed(pet.getBreed());
        petInfo.setWeight(pet.getWeight());
        petInfo.setGender(genderText(pet.getGender()));
        fillAge(petInfo, pet.getBirthday());
        return petInfo;
    }

    /**
     * 年龄：满 1 岁传年龄（岁），不足 1 岁传月龄，无生日则不传
     */
    private void fillAge(RagPetInfo petInfo, LocalDate birthday) {
        if (birthday == null) {
            return;
        }
        Period period = Period.between(birthday, LocalDate.now());
        if (period.getYears() >= 1) {
            petInfo.setAge(period.getYears());
        } else {
            petInfo.setAgeMonths(period.getMonths() + period.getYears() * 12);
        }
    }

    private String genderText(Integer gender) {
        if (gender == null) {
            return null;
        }
        if (gender == GENDER_MALE) {
            return "公";
        }
        if (gender == GENDER_FEMALE) {
            return "母";
        }
        return null;
    }

    /**
     * 只取最近 3 轮（最多 6 条）对话历史，避免上下文过长
     */
    private List<RagChatMessage> buildConversationHistory(List<ConversationMessageDTO> history) {
        if (CollectionUtils.isEmpty(history)) {
            return new ArrayList<>();
        }
        int fromIndex = Math.max(history.size() - HISTORY_MAX_ROUNDS * 2, 0);
        return history.subList(fromIndex, history.size()).stream()
                .map(item -> {
                    RagChatMessage message = new RagChatMessage();
                    message.setRole(item.getRole());
                    message.setContent(item.getContent());
                    return message;
                })
                .collect(Collectors.toList());
    }

    private List<KnowledgeReferenceVO> toReferences(List<RagReference> ragReferences) {
        if (CollectionUtils.isEmpty(ragReferences)) {
            return new ArrayList<>();
        }
        return ragReferences.stream()
                .map(reference -> {
                    KnowledgeReferenceVO vo = new KnowledgeReferenceVO();
                    vo.setDocId(reference.getDocId());
                    vo.setTitle(reference.getTitle());
                    vo.setContent(reference.getContent());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private AiConversationVO toVO(AiConversation entity) {
        AiConversationVO vo = new AiConversationVO();
        vo.setId(entity.getId());
        vo.setPetId(entity.getPetId());
        vo.setQuestion(entity.getQuestion());
        vo.setAnswer(entity.getAnswer());
        vo.setCreateTime(entity.getCreateTime());
        vo.setReferences(parseReferences(entity.getReferences()));
        return vo;
    }

    /**
     * 解析历史记录中的参考来源 JSON，格式异常时返回空列表，不影响历史列表展示
     */
    private List<KnowledgeReferenceVO> parseReferences(String referencesJson) {
        if (StringUtils.isBlank(referencesJson)) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseArray(referencesJson, KnowledgeReferenceVO.class);
        } catch (Exception e) {
            log.warn("解析参考来源失败: {}", StringUtils.abbreviate(referencesJson, 200), e);
            return new ArrayList<>();
        }
    }

}
