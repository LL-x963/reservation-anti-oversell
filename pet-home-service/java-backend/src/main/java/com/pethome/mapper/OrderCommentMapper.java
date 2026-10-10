package com.pethome.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pethome.entity.OrderComment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

/**
 * 订单评价 Mapper
 *
 * @author pet-home
 */
public interface OrderCommentMapper extends BaseMapper<OrderComment> {

    /**
     * 统计某美容师的平均评分
     *
     * @param beauticianId 美容师 id
     * @return 平均分，无评价记录时返回 null
     */
    @Select("SELECT AVG(score) FROM order_comment WHERE beautician_id = #{beauticianId}")
    BigDecimal avgScoreByBeauticianId(@Param("beauticianId") Long beauticianId);

}
