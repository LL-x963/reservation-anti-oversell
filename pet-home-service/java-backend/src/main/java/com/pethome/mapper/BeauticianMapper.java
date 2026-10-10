package com.pethome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pethome.entity.Beautician;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 美容师 Mapper
 *
 * @author pet-home
 */
public interface BeauticianMapper extends BaseMapper<Beautician> {

    /**
     * 统计指定美容师关联的未完成订单数量
     *
     * <p>未完成订单状态：0待支付、1待确认、2待上门、3服务中。</p>
     *
     * @param beauticianId 美容师 id
     * @return 未完成订单数量
     */
    @Select("SELECT COUNT(1) FROM service_order WHERE beautician_id = #{beauticianId} AND status IN (1, 2, 3, 4)")
    long countUnfinishedOrderByBeauticianId(@Param("beauticianId") Long beauticianId);

}
