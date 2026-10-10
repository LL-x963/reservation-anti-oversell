package com.pethome.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 前端传入的对话历史消息
 *
 * @author pet-home
 */
@Data
public class ConversationMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色：user / assistant */
    @NotBlank(message = "对话角色不能为空")
    @Pattern(regexp = "^(user|assistant)$", message = "对话角色只能是 user 或 assistant")
    private String role;

    /** 消息内容 */
    @NotBlank(message = "对话内容不能为空")
    @Size(max = 2000, message = "对话内容长度不能超过2000")
    private String content;

}
