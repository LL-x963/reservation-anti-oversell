package com.pethome.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.pethome.common.context.UserContext;
import com.pethome.common.enums.IndexStatus;
import com.pethome.common.enums.UserType;
import com.pethome.common.exception.BusinessException;
import com.pethome.common.exception.KnowledgeServiceException;
import com.pethome.dto.AiConversationPageQuery;
import com.pethome.dto.ConversationMessageDTO;
import com.pethome.dto.KnowledgeAskRequest;
import com.pethome.entity.AiConversation;
import com.pethome.entity.KnowledgeDoc;
import com.pethome.entity.User;
import com.pethome.mapper.UserMapper;
import com.pethome.rag.KnowledgeClient;
import com.pethome.rag.dto.RagAskRequest;
import com.pethome.rag.dto.RagAskResponse;
import com.pethome.rag.dto.RagReference;
import com.pethome.vo.AiConversationVO;
import com.pethome.vo.KnowledgeAskVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库问答服务层测试
 *
 * <p>RAG 客户端用 Mock 替换（本地不需要启动 rag-service），其余走真实数据库。
 * 类上带 @Transactional，数据自动回滚。</p>
 *
 * @author pet-home
 */
@SpringBootTest
@Transactional
class KnowledgeServiceTest {

    private static final String DOC_TITLE_PREFIX = "IT-AI-知识库-";
    private static final String PET_PREFIX = "IT-AI-宠物-";

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private KnowledgeDocService knowledgeDocService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private KnowledgeClient knowledgeClient;

    private Long userAId;
    private Long userBId;

    @BeforeEach
    void setUp() {
        userAId = insertUser("IT-AI-USER-A");
        userBId = insertUser("IT-AI-USER-B");
        // 模拟登录拦截器写入 ThreadLocal
        UserContext.set(userAId, UserType.USER);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    // ==================== 提问 ====================

    @Test
    void askShouldCallRagWithEnabledDocsAndSaveHistory() {
        Long normalDocId = addDoc(DOC_TITLE_PREFIX + "正常", 1, IndexStatus.NORMAL.getCode());
        Long notIndexedDocId = addDoc(DOC_TITLE_PREFIX + "未索引", 1, IndexStatus.NOT_INDEXED.getCode());
        Long disabledDocId = addDoc(DOC_TITLE_PREFIX + "已禁用", 0, IndexStatus.NORMAL.getCode());
        Long petId = insertPet(userAId, PET_PREFIX + "团子", "英短", 1, LocalDate.now().minusYears(2), "5.00");

        when(knowledgeClient.ask(any())).thenReturn(ragResponse());

        KnowledgeAskRequest request = new KnowledgeAskRequest();
        request.setQuestion("猫咪掉毛怎么办");
        request.setPetId(petId);
        request.setConversationHistory(buildHistory(8));

        KnowledgeAskVO vo = knowledgeService.ask(request);
        assertThat(vo.getAnswer()).contains("建议每天给猫咪梳毛");
        assertThat(vo.getReferences()).hasSize(2);
        assertThat(vo.getReferences().get(0).getDocId()).isEqualTo(1L);
        assertThat(vo.getReferences().get(0).getTitle()).isEqualTo("猫咪护理");

        // 传给 RAG 的请求内容
        ArgumentCaptor<RagAskRequest> captor = ArgumentCaptor.forClass(RagAskRequest.class);
        verify(knowledgeClient).ask(captor.capture());
        RagAskRequest ragRequest = captor.getValue();
        assertThat(ragRequest.getQuestion()).isEqualTo("猫咪掉毛怎么办");
        assertThat(ragRequest.getUserId()).isEqualTo(userAId);
        // 只有 enabled=1 且 index_status=2 的文档才会传给 RAG
        assertThat(ragRequest.getEnabledDocIds()).contains(normalDocId);
        assertThat(ragRequest.getEnabledDocIds()).doesNotContain(notIndexedDocId, disabledDocId);
        // 历史只取最近 3 轮（6 条）
        assertThat(ragRequest.getConversationHistory()).hasSize(6);
        assertThat(ragRequest.getConversationHistory().get(0).getContent()).isEqualTo("问题1");
        // 宠物信息
        assertThat(ragRequest.getPetInfo()).isNotNull();
        assertThat(ragRequest.getPetInfo().getName()).isEqualTo(PET_PREFIX + "团子");
        assertThat(ragRequest.getPetInfo().getBreed()).isEqualTo("英短");
        assertThat(ragRequest.getPetInfo().getAge()).isEqualTo(2);
        assertThat(ragRequest.getPetInfo().getWeight()).isEqualByComparingTo("5.00");
        assertThat(ragRequest.getPetInfo().getGender()).isEqualTo("公");

        // 问答历史落库（同时验证 references 列写入与读取）
        IPage<AiConversationVO> page = knowledgeService.pageHistory(new AiConversationPageQuery());
        assertThat(page.getTotal()).isEqualTo(1);
        AiConversationVO history = page.getRecords().get(0);
        assertThat(history.getQuestion()).isEqualTo("猫咪掉毛怎么办");
        assertThat(history.getAnswer()).contains("建议每天给猫咪梳毛");
        assertThat(history.getPetId()).isEqualTo(petId);
        assertThat(history.getReferences()).hasSize(2);
        assertThat(history.getReferences().get(1).getTitle()).isEqualTo("驱虫指南");
        assertThat(history.getCreateTime()).isNotNull();
    }

    @Test
    void askShouldSendMonthAgeWhenPetYoungerThanOneYear() {
        Long petId = insertPet(userAId, PET_PREFIX + "奶猫", "布偶", 2, LocalDate.now().minusMonths(5), null);
        when(knowledgeClient.ask(any())).thenReturn(ragResponse());

        KnowledgeAskRequest request = new KnowledgeAskRequest();
        request.setQuestion("小猫能洗澡吗");
        request.setPetId(petId);
        knowledgeService.ask(request);

        ArgumentCaptor<RagAskRequest> captor = ArgumentCaptor.forClass(RagAskRequest.class);
        verify(knowledgeClient).ask(captor.capture());
        assertThat(captor.getValue().getPetInfo().getAge()).isNull();
        assertThat(captor.getValue().getPetInfo().getAgeMonths()).isEqualTo(5);
        assertThat(captor.getValue().getPetInfo().getGender()).isEqualTo("母");
    }

    @Test
    void askShouldNotSendPetInfoWhenPetIdMissing() {
        when(knowledgeClient.ask(any())).thenReturn(ragResponse());

        KnowledgeAskRequest request = new KnowledgeAskRequest();
        request.setQuestion("狗狗能吃苹果吗");
        knowledgeService.ask(request);

        ArgumentCaptor<RagAskRequest> captor = ArgumentCaptor.forClass(RagAskRequest.class);
        verify(knowledgeClient).ask(captor.capture());
        assertThat(captor.getValue().getPetInfo()).isNull();
        assertThat(captor.getValue().getConversationHistory()).isEmpty();
    }

    @Test
    void askShouldRejectPetOfOtherUser() {
        Long petId = insertPet(userBId, PET_PREFIX + "别人的", "金毛", 2, null, null);

        KnowledgeAskRequest request = new KnowledgeAskRequest();
        request.setQuestion("这只狗怎么了");
        request.setPetId(petId);

        assertThatThrownBy(() -> knowledgeService.ask(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("宠物不存在");
        verify(knowledgeClient, never()).ask(any());
        assertThat(knowledgeService.pageHistory(new AiConversationPageQuery()).getTotal()).isZero();
    }

    @Test
    void askShouldThrowKnowledgeServiceExceptionAndNotSaveHistory() {
        when(knowledgeClient.ask(any())).thenThrow(new KnowledgeServiceException());

        KnowledgeAskRequest request = new KnowledgeAskRequest();
        request.setQuestion("猫咪掉毛怎么办");

        assertThatThrownBy(() -> knowledgeService.ask(request))
                .isInstanceOf(KnowledgeServiceException.class)
                .hasMessage(KnowledgeServiceException.MESSAGE);
        assertThat(knowledgeService.pageHistory(new AiConversationPageQuery()).getTotal()).isZero();
    }

    @Test
    void askShouldFailWhenRagAnswerIsBlank() {
        RagAskResponse blank = new RagAskResponse();
        blank.setAnswer("   ");
        when(knowledgeClient.ask(any())).thenReturn(blank);

        KnowledgeAskRequest request = new KnowledgeAskRequest();
        request.setQuestion("猫咪掉毛怎么办");

        assertThatThrownBy(() -> knowledgeService.ask(request))
                .isInstanceOf(KnowledgeServiceException.class)
                .hasMessage(KnowledgeServiceException.MESSAGE);
        assertThat(knowledgeService.pageHistory(new AiConversationPageQuery()).getTotal()).isZero();
    }

    @Test
    void askShouldFailWhenNotLoggedIn() {
        UserContext.clear();

        KnowledgeAskRequest request = new KnowledgeAskRequest();
        request.setQuestion("猫咪掉毛怎么办");

        assertThatThrownBy(() -> knowledgeService.ask(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未获取到登录用户");
    }

    // ==================== 历史记录 ====================

    @Test
    void historyShouldBeIsolatedByUserAndSupportDeleteAndClear() {
        Long ownId = saveHistory(userAId, "A的问题1");
        saveHistory(userAId, "A的问题2");
        Long otherId = saveHistory(userBId, "B的问题");

        IPage<AiConversationVO> page = knowledgeService.pageHistory(new AiConversationPageQuery());
        assertThat(page.getTotal()).isEqualTo(2);
        // 按提问时间倒序（同一秒内按 id 倒序）
        assertThat(page.getRecords().get(0).getQuestion()).isEqualTo("A的问题2");

        // 删除别人的记录 -> 拒绝且不生效
        assertThatThrownBy(() -> knowledgeService.deleteHistory(otherId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("问答记录不存在");
        assertThat(knowledgeService.pageHistory(new AiConversationPageQuery()).getTotal()).isEqualTo(2);

        // 删除自己的记录
        knowledgeService.deleteHistory(ownId);
        assertThat(knowledgeService.pageHistory(new AiConversationPageQuery()).getTotal()).isEqualTo(1);

        // 清空只清当前用户
        knowledgeService.clearHistory();
        assertThat(knowledgeService.pageHistory(new AiConversationPageQuery()).getTotal()).isZero();

        UserContext.set(userBId, UserType.USER);
        assertThat(knowledgeService.pageHistory(new AiConversationPageQuery()).getTotal()).isEqualTo(1);
        UserContext.set(userAId, UserType.USER);
    }

    @Test
    void historyShouldBeSortedByCreateTimeDesc() {
        saveHistory(userAId, "第一条");
        saveHistory(userAId, "第二条");
        saveHistory(userAId, "第三条");

        List<AiConversationVO> records = knowledgeService.pageHistory(new AiConversationPageQuery()).getRecords();

        assertThat(records).hasSize(3);
        assertThat(records.get(0).getQuestion()).isEqualTo("第三条");
        assertThat(records.get(2).getQuestion()).isEqualTo("第一条");
    }

    // ==================== 辅助方法 ====================

    private RagAskResponse ragResponse() {
        RagReference first = new RagReference();
        first.setDocId(1L);
        first.setTitle("猫咪护理");
        first.setContent("每周梳毛2-3次");

        RagReference second = new RagReference();
        second.setDocId(2L);
        second.setTitle("驱虫指南");
        second.setContent("每3个月体内驱虫一次");

        RagAskResponse response = new RagAskResponse();
        response.setAnswer("建议每天给猫咪梳毛，并按时驱虫。");
        response.setReferences(new ArrayList<>(List.of(first, second)));
        return response;
    }

    private List<ConversationMessageDTO> buildHistory(int size) {
        List<ConversationMessageDTO> history = new ArrayList<>();
        for (int i = 0; i < size / 2; i++) {
            history.add(message("user", "问题" + i));
            history.add(message("assistant", "回答" + i));
        }
        return history;
    }

    private ConversationMessageDTO message(String role, String content) {
        ConversationMessageDTO message = new ConversationMessageDTO();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private Long saveHistory(Long userId, String question) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setQuestion(question);
        conversation.setAnswer(question + "的答案");
        conversation.setReferences("[{\"docId\":1,\"title\":\"猫咪护理\",\"content\":\"片段\"}]");
        knowledgeService.save(conversation);
        return conversation.getId();
    }

    private Long addDoc(String title, int enabled, int indexStatus) {
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setTitle(title);
        doc.setCategory("护理");
        doc.setContent("每周梳毛2-3次。");
        doc.setEnabled(enabled);
        doc.setIndexStatus(indexStatus);
        knowledgeDocService.save(doc);
        return doc.getId();
    }

    private Long insertUser(String openid) {
        User user = new User();
        user.setOpenid(openid);
        user.setSex(0);
        userMapper.insert(user);
        return user.getId();
    }

    private Long insertPet(Long userId, String name, String breed, Integer gender, LocalDate birthday, String weight) {
        jdbcTemplate.update("INSERT INTO pet_profile (user_id, name, breed, gender, birthday, weight, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NOW(), NOW())",
                userId, name, breed, gender, birthday == null ? null : Date.valueOf(birthday),
                weight == null ? null : new BigDecimal(weight));
        return jdbcTemplate.queryForObject("SELECT id FROM pet_profile WHERE user_id = ? AND name = ?",
                Long.class, userId, name);
    }

}
