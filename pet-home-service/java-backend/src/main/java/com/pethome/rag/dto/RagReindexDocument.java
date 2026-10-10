package com.pethome.rag.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 参与重建索引的知识库文档
 *
 * @author pet-home
 */
@Data
public class RagReindexDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文档 id */
    private Long id;

    /** 标题 */
    private String title;

    /** 分类 */
    private String category;

    /** 正文 */
    private String content;

}
