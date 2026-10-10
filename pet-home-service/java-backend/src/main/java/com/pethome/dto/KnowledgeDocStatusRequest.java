package com.pethome.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 知识库文档启用/禁用请求
 *
 * @author pet-home
 */
@Data
public class KnowledgeDocStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 文档 id */
    @NotNull(message = "文档id不能为空")
    @Min(value = 1, message = "文档id不合法")
    private Long id;

    /** 是否启用 1启用 0禁用 */
    @NotNull(message = "启用状态不能为空")
    @Min(value = 0, message = "启用状态只能为1（启用）或0（禁用）")
    @Max(value = 1, message = "启用状态只能为1（启用）或0（禁用）")
    private Integer enabled;

}
