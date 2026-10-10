package com.pethome.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 修改美容师请求（全量更新，不包含评分字段）
 *
 * @author pet-home
 */
@Data
public class BeauticianUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 美容师 id */
    @NotNull(message = "美容师id不能为空")
    @Min(value = 1, message = "美容师id不合法")
    private Long id;

    /** 姓名 */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50")
    private String name;

    /** 头像地址 */
    @Size(max = 255, message = "头像地址长度不能超过255")
    @Pattern(regexp = "^(https?://|/).+", message = "头像地址必须以 http(s):// 或 / 开头")
    private String avatar;

    /** 技能标签，服务端会去重、去空并拼接为逗号分隔字符串存储 */
    @Size(max = 20, message = "技能标签数量不能超过20个")
    private List<@Size(max = 50, message = "单个技能标签长度不能超过50") String> skillTags;

    /** 服务范围 */
    @Size(max = 255, message = "服务范围长度不能超过255")
    private String serviceRange;

    /** 入职时间，格式 yyyy-MM-dd */
    @PastOrPresent(message = "入职时间不能晚于今天")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate entryTime;

    /** 状态 1在职 0离职 */
    @NotNull(message = "状态不能为空")
    @Min(value = 0, message = "状态只能为1（在职）或0（离职）")
    @Max(value = 1, message = "状态只能为1（在职）或0（离职）")
    private Integer status;

}
