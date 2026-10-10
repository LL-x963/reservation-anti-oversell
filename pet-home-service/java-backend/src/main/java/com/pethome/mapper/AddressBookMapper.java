package com.pethome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pethome.entity.AddressBook;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 地址簿 Mapper
 *
 * <p>地址簿模块（C 端增删改查）后续补全；当前提供下单归属校验与订单详情回显所需的按 id 查询能力。</p>
 *
 * @author pet-home
 */
public interface AddressBookMapper extends BaseMapper<AddressBook> {

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
