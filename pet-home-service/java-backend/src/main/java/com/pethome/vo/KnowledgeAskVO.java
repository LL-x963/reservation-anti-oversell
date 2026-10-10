package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户端知识库问答结果
 *
 * @author pet-home
 */
@Data
public class KnowledgeAskVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 回答内容（末尾含免责声明） */
    private String answer;

    /** 参考来源 */
    private List<KnowledgeReferenceVO> references = new ArrayList<>();

}
