package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户端地址簿信息
 *
 * @author pet-home
 */
@Data
public class AddressBookVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 收货联系人 */
    private String consignee;

    /** 联系电话 */
    private String phone;

    /** 完整地址：省市区 + 详细地址 */
    private String fullAddress;

    /** 省（编辑时回填） */
    private String provinceName;

    /** 市（编辑时回填） */
    private String cityName;

    /** 区县（编辑时回填） */
    private String districtName;

    /** 详细地址（编辑时回填） */
    private String detail;

    /** 标签，如「家」「公司」 */
    private String label;

    /** 是否默认地址 1是 0否 */
    private Integer isDefault;

}
