package com.pethome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pethome.entity.PetProfile;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 宠物档案 Mapper
 *
 * @author pet-home
 */
public interface PetProfileMapper extends BaseMapper<PetProfile> {

    /**
     * 统计指定宠物关联的未完成订单数量
     *
     * <p>未完成订单状态：0待支付、1待确认、2待上门、3服务中。</p>
     *
     * @param petId 宠物 id
     * @return 未完成订单数量
     */
    @Select("SELECT COUNT(1) FROM service_order WHERE pet_id = #{petId} AND status IN (0, 1, 2, 3)")
    long countUnfinishedOrderByPetId(@Param("petId") Long petId);

}
