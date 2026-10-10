package com.pethome.dto;

import com.pethome.common.enums.KnowledgeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 新增知识库文档请求
 *
 * @author pet-home
 */
@Data
public class KnowledgeDocAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文档标题，全局唯一 */
    @NotBlank(message = "文档标题不能为空")
    @Size(max = 200, message = "文档标题长度不能超过200")
    private String title;

    /** 分类，固定取值：护理/饮食/疾病预防 */
    @NotBlank(message = "文档分类不能为空")
    @Size(max = 50, message = "文档分类长度不能超过50")
    @Pattern(regexp = KnowledgeCategory.PATTERN, message = "文档分类只能是：护理、饮食、疾病预防")
    private String category;

    /** 文档内容 */
    @NotBlank(message = "文档内容不能为空")
    @Size(max = 20000, message = "文档内容长度不能超过20000")
    private String content;

}
