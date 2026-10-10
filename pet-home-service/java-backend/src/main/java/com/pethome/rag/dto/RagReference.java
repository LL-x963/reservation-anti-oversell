package com.pethome.rag.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * RAG 返回的参考来源片段
 *
 * @author pet-home
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RagReference implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 来源文档 id */
    private Long docId;

    /** 来源文档标题 */
    private String title;

    /** 命中的片段内容 */
    private String content;

}
