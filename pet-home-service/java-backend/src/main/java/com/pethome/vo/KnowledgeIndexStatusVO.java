package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 知识库索引状态概览
 *
 * @author pet-home
 */
@Data
public class KnowledgeIndexStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文档总数 */
    private Long totalCount;

    /** 启用文档数（参与索引） */
    private Long enabledCount;

    /** 禁用文档数（不参与索引与检索） */
    private Long disabledCount;

    /** 未索引数量 */
    private Long notIndexedCount;

    /** 索引中数量 */
    private Long indexingCount;

    /** 索引正常数量 */
    private Long normalCount;

    /** 索引失败数量 */
    private Long failedCount;

    /** 是否正在重建索引（存在索引中的文档即为 true） */
    private Boolean rebuilding;

}
