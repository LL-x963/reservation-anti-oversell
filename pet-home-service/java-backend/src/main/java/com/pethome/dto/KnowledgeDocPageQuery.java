package com.pethome.dto;

import com.pethome.common.enums.KnowledgeCategory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端知识库文档分页查询条件
 *
 * @author pet-home
 */
@Data
public class KnowledgeDocPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    /** 每页条数 */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer pageSize = 10;

    /** 分类筛选，固定取值：护理/饮食/疾病预防 */
    @Size(max = 50, message = "文档分类长度不能超过50")
    @Pattern(regexp = KnowledgeCategory.PATTERN, message = "文档分类只能是：护理、饮食、疾病预防")
    private String category;

    /** 启用状态筛选 1启用 0禁用 */
    @Min(value = 0, message = "启用状态只能为1（启用）或0（禁用）")
    @Max(value = 1, message = "启用状态只能为1（启用）或0（禁用）")
    private Integer enabled;

}
