package com.pethome.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 地址新增 / 修改请求
 *
 * @author pet-home
 */
@Data
public class AddressSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 联系人 */
    @NotBlank(message = "请输入联系人")
    @Size(max = 50, message = "联系人长度不能超过 50")
    private String consignee;

    /** 联系电话 */
    @NotBlank(message = "请输入联系电话")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 性别 0未知 1男 2女 */
    private Integer sex;

    /** 省 */
    @Size(max = 50, message = "省份名称过长")
    private String provinceName;

    /** 市 */
    @Size(max = 50, message = "城市名称过长")
    private String cityName;

    /** 区县 */
    @Size(max = 50, message = "区县名称过长")
    private String districtName;

    /** 详细地址 */
    @NotBlank(message = "请输入详细地址")
    @Size(max = 255, message = "详细地址长度不能超过 255")
    private String detail;

    /** 标签：家 / 公司 等 */
    @Size(max = 50, message = "标签长度不能超过 50")
    private String label;

    /** 是否设为默认地址 */
    private Boolean isDefault;

}
