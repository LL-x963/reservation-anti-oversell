package com.pethome.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端美容师分页查询条件
 *
 * @author pet-home
 */
@Data
public class BeauticianPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    /** 每页条数 */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer pageSize = 10;

    /** 姓名模糊筛选 */
    @Size(max = 50, message = "姓名长度不能超过50")
    private String name;

    /** 技能标签精确筛选（匹配逗号分隔存储中的某一项） */
    @Size(max = 50, message = "技能标签长度不能超过50")
    private String skillTag;

    /** 状态筛选 1在职 0离职 */
    @Min(value = 0, message = "状态只能为1（在职）或0（离职）")
    @Max(value = 1, message = "状态只能为1（在职）或0（离职）")
    private Integer status;

}
