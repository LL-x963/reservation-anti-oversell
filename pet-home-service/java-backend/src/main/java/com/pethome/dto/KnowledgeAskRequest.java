package com.pethome.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户端知识库问答请求
 *
 * @author pet-home
 */
@Data
public class KnowledgeAskRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户问题 */
    @NotBlank(message = "问题内容不能为空")
    @Size(max = 1000, message = "问题长度不能超过1000")
    private String question;

    /**
     * 对话历史，前端传最近 3 轮（最多 6 条）
     *
     * <p>超出部分服务端只取最后 3 轮。</p>
     */
    @Valid
    @Size(max = 20, message = "对话历史最多20条")
    private List<ConversationMessageDTO> conversationHistory = new ArrayList<>();

    /** 关联宠物 id（可选），传了会把宠物信息一并带给模型 */
    @Min(value = 1, message = "宠物id不合法")
    private Long petId;

}
