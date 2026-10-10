package com.pethome.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 新增服务分类请求
 *
 * @author pet-home
 */
@Data
public class ServiceCategoryAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 分类名称 */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称长度不能超过50")
    private String name;

    /** 排序值，不传默认 0 */
    @Min(value = 0, message = "排序值不能小于0")
    private Integer sort;

    /** 状态 1启用 0禁用，不传默认 1 */
    @Min(value = 0, message = "状态只能为1（启用）或0（禁用）")
    @Max(value = 1, message = "状态只能为1（启用）或0（禁用）")
    private Integer status;

}
