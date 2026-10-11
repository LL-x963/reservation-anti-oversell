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

    /** 标签，如「家」「公司」 */
    private String label;

    /** 是否默认地址 1是 0否 */
    private Integer isDefault;

}
