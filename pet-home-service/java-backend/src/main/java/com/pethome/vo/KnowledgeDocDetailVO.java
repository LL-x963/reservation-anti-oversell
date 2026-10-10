package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端知识库文档详情（含正文 content）
 *
 * @author pet-home
 */
@Data
public class KnowledgeDocDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String title;

    /** 分类：护理/饮食/疾病预防 */
    private String category;

    /** 文档内容 */
    private String content;

    /** 是否启用 1启用 0禁用 */
    private Integer enabled;

    /** 索引状态 0未索引 1索引中 2正常 3失败 */
    private Integer indexStatus;

    /** 索引状态描述 */
    private String indexStatusDesc;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
