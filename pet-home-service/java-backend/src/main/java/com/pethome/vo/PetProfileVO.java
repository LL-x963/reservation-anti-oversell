package com.pethome.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 宠物档案信息
 *
 * @author pet-home
 */
@Data
public class PetProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 归属用户 id */
    private Long userId;

    private String name;

    private String breed;

    /** 性别 0未知 1公 2母 */
    private Integer gender;

    /** 生日 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    /** 体重(kg) */
    private BigDecimal weight;

    private String vaccine;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

}
