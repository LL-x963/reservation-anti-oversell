package com.pethome.mapper;

import com.pethome.vo.BeauticianRankVO;
import com.pethome.vo.RepurchaseVO;
import com.pethome.vo.ServiceRankVO;
import com.pethome.vo.StatRevenueItemVO;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据统计 Mapper（聚合查询，SQL 见 resources/mapper/StatMapper.xml）
 *
 * @author pet-home
 */
public interface StatMapper {

    /**
     * 按自然日统计营业额（按下单支付时间归期）
     *
     * @param start    起始时间（含）
     * @param end      结束时间（不含）
     * @param statuses 计入统计的订单状态集合（已完成口径）
     */
    List<StatRevenueItemVO> selectRevenueByDay(@Param("start") LocalDateTime start,
                                               @Param("end") LocalDateTime end,
                                               @Param("statuses") List<Integer> statuses);

    /**
     * 服务销量排行（按销量降序）
     *
     * @param statuses 计入统计的订单状态集合
     * @param limit    取前 N 条
     */
    List<ServiceRankVO> selectServiceRank(@Param("statuses") List<Integer> statuses,
                                          @Param("limit") int limit);

    /**
     * 全部服务总销量（用于计算销量占比的分母）
     */
    BigDecimal selectTotalSales(@Param("statuses") List<Integer> statuses);

    /**
     * 美容师业绩排行（按营业额降序）
     */
    List<BeauticianRankVO> selectBeauticianRank(@Param("statuses") List<Integer> statuses);

    /**
     * 用户复购统计：总用户数（有已完成订单）与复购用户数（已完成订单 ≥ 2 笔）
     */
    RepurchaseVO selectRepurchase(@Param("statuses") List<Integer> statuses);

}
