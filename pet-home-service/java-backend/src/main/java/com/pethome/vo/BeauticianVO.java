package com.pethome.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 美容师信息（技能标签以数组返回）
 *
 * @author pet-home
 */
@Data
public class BeauticianVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String name;

    private String avatar;

    /** 技能标签 */
    private List<String> skillTags;

    /** 服务范围 */
    private String serviceRange;

    /** 入职时间 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate entryTime;

    /** 评分 */
    private BigDecimal score;

    /** 状态 1在职 0离职 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
