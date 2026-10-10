package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档表
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("knowledge_doc")
public class KnowledgeDoc extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 文档标题，全局唯一（uk_title） */
    private String title;

    /** 分类，固定取值：护理/饮食/疾病预防 */
    private String category;

    /** 文档内容 */
    private String content;

    /** 是否启用 1启用 0禁用（禁用的文档不参与索引与检索） */
    private Integer enabled;

    /** 索引状态 0未索引 1索引中 2正常 3失败 */
    private Integer indexStatus;

}
