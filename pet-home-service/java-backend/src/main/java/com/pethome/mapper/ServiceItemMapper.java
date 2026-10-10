package com.pethome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pethome.entity.ServiceItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 服务项目 Mapper
 *
 * @author pet-home
 */
public interface ServiceItemMapper extends BaseMapper<ServiceItem> {

    /**
     * 统计指定分类下的服务项目数量（包含上架与下架），供服务分类删除校验使用
     *
     * @param categoryId 分类 id
     * @return 服务项目数量
     */
    @Select("SELECT COUNT(1) FROM service_item WHERE category_id = #{categoryId}")
    long countByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 统计指定服务项目关联的未完成订单数量
     *
     * <p>未完成订单状态：0待支付、1待确认、2待上门、3服务中。</p>
     *
     * @param serviceId 服务项目 id
     * @return 未完成订单数量
     */
    @Select("SELECT COUNT(1) FROM order_detail od "
            + "INNER JOIN service_order o ON o.id = od.order_id "
            + "WHERE od.service_id = #{serviceId} AND o.status IN (0, 1, 2, 3)")
    long countUnfinishedOrderByServiceId(@Param("serviceId") Long serviceId);

}
