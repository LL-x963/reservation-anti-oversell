package com.pethome.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 地址簿 Mapper（当前仅提供下单所需的地址归属校验能力，地址簿模块后续补全）
 *
 * @author pet-home
 */
public interface AddressBookMapper {

    /**
     * 统计指定用户名下的地址数量
     *
     * @param addressId 地址 id
     * @param userId    用户 id
     * @return 命中数量
     */
    @Select("SELECT COUNT(1) FROM address_book WHERE id = #{addressId} AND user_id = #{userId}")
    long countByIdAndUserId(@Param("addressId") Long addressId, @Param("userId") Long userId);

}
