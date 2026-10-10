package com.pethome.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户地址簿
 *
 * <p>订单详情需要回显收货信息（联系人 / 电话 / 完整地址），此前仅通过
 * {@code AddressBookMapper} 做归属校验，这里补齐实体以便按 id 查询。</p>
 *
 * @author pet-home
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("address_book")
public class AddressBook extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 所属用户 id */
    private Long userId;

    /** 联系人 */
    private String consignee;

    /** 性别 0未知 1男 2女 */
    private Integer sex;

    /** 手机号 */
    private String phone;

    /** 省份编码 */
    private String provinceCode;

    /** 省份名称 */
    private String provinceName;

    /** 城市编码 */
    private String cityCode;

    /** 城市名称 */
    private String cityName;

    /** 区县编码 */
    private String districtCode;

    /** 区县名称 */
    private String districtName;

    /** 详细地址 */
    private String detail;

    /** 标签 */
    private String label;

    /** 是否默认 1是 0否 */
    private Integer isDefault;

}
