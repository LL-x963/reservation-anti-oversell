package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识库问答的参考来源
 *
 * @author pet-home
 */
@Data
public class KnowledgeReferenceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源文档 id */
    private Long docId;

    /** 来源文档标题 */
    private String title;

    /** 命中的片段内容 */
    private String content;

}
