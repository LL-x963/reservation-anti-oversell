package com.pethome.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新增宠物请求
 *
 * <p>user_id 由服务端根据登录态自动绑定，不接受客户端传入。</p>
 *
 * @author pet-home
 */
@Data
public class PetProfileAddRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 宠物名称 */
    @NotBlank(message = "宠物名称不能为空")
    @Size(max = 50, message = "宠物名称长度不能超过50")
    private String name;

    /** 品种 */
    @Size(max = 50, message = "品种长度不能超过50")
    private String breed;

    /** 性别 0未知 1公 2母 */
    @Min(value = 0, message = "性别只能为0（未知）、1（公）或2（母）")
    @Max(value = 2, message = "性别只能为0（未知）、1（公）或2（母）")
    private Integer gender;

    /** 生日，格式 yyyy-MM-dd */
    @PastOrPresent(message = "生日不能晚于今天")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    /** 体重(kg) */
    @DecimalMin(value = "0.01", message = "体重必须大于0")
    @DecimalMax(value = "999.99", message = "体重不能超过999.99kg")
    @Digits(integer = 3, fraction = 2, message = "体重整数位不能超过3位，小数位不能超过2位")
    private BigDecimal weight;

    /** 疫苗情况 */
    @Size(max = 255, message = "疫苗情况长度不能超过255")
    private String vaccine;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;

}
