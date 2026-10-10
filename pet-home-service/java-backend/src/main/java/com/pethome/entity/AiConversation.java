package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 问答历史表
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
public class AiConversation extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 提问用户 id，由服务端按登录态写入 */
    private Long userId;

    /** 关联宠物 id（提问时可空） */
    private Long petId;

    /** 用户问题 */
    private String question;

    /** 模型回答（末尾含免责声明） */
    private String answer;

    /**
     * 参考来源 JSON 数组：{@code [{"docId":1,"title":"..","content":".."}]}
     *
     * <p>references 是 MySQL 保留字，列名必须用反引号包裹，否则 SQL 会解析失败。</p>
     */
    @TableField("`references`")
    private String references;

}
