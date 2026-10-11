package com.pethome.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 用户端美容师信息（仅返回选择所需字段，不暴露手机号等隐私信息）
 *
 * @author pet-home
 */
@Data
public class BeauticianSimpleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 姓名 */
    private String name;

    /** 头像 */
    private String avatar;

    /** 评分 */
    private BigDecimal score;

}
