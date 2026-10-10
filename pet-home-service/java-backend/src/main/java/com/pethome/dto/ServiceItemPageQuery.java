package com.pethome.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 管理端服务项目分页查询条件
 *
 * @author pet-home
 */
@Data
public class ServiceItemPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 页码，从 1 开始 */
    @Min(value = 1, message = "页码不能小于1")
    private Integer page = 1;

    /** 每页条数 */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer pageSize = 10;

    /** 分类 id 筛选 */
    @Min(value = 1, message = "分类id不合法")
    private Long categoryId;

    /** 服务名称模糊筛选 */
    @Size(max = 100, message = "服务名称长度不能超过100")
    private String name;

    /** 状态筛选 1上架 0下架 */
    @Min(value = 0, message = "状态只能为1（上架）或0（下架）")
    @Max(value = 1, message = "状态只能为1（上架）或0（下架）")
    private Integer status;

}
