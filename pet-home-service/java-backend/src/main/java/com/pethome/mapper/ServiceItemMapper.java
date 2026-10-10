package com.pethome.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 服务项目 Mapper（当前仅提供服务分类模块所需的关联统计能力，服务项目模块后续补全）
 *
 * @author pet-home
 */
public interface ServiceItemMapper {

    /**
     * 统计指定分类下的服务项目数量（包含上架与下架）
     *
     * @param categoryId 分类 id
     * @return 服务项目数量
     */
    @Select("SELECT COUNT(1) FROM service_item WHERE category_id = #{categoryId}")
    long countByCategoryId(@Param("categoryId") Long categoryId);

}
