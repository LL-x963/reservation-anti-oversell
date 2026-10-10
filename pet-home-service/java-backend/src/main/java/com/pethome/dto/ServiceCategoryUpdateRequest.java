package com.pethome.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 修改服务分类请求（全量更新）
 *
 * @author pet-home
 */
@Data
public class ServiceCategoryUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分类 id */
    @NotNull(message = "分类id不能为空")
    @Min(value = 1, message = "分类id不合法")
    private Long id;

    /** 分类名称 */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50")
    private String name;

    /** 排序值 */
    @NotNull(message = "排序值不能为空")
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sort;

    /** 状态 1启用 0禁用 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为1（启用）或0（禁用）")
    @Max(value = 1, message = "状态只能为1（启用）或0（禁用）")
    private Integer status;

}
