package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户端问答历史
 *
 * @author pet-home
 */
@Data
public class AiConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 提问时关联的宠物 id（可为空） */
    private Long petId;

    private String question;

    private String answer;

    /** 参考来源（当时记录的片段） */
    private List<KnowledgeReferenceVO> references = new ArrayList<>();

    private LocalDateTime createTime;

}
