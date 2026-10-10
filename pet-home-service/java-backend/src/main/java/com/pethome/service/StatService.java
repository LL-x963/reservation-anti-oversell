package com.pethome.service;

import com.pethome.vo.BeauticianRankVO;
import com.pethome.vo.RepurchaseVO;
import com.pethome.vo.ServiceRankVO;
import com.pethome.vo.StatRevenueVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 数据统计服务（管理端，仅统计已完成订单）
 *
 * @author pet-home
 */
public interface StatService {

    /**
     * 营业额统计：以 date 为基准，按 day/week/month 确定区间，返回区间内逐日营业额与总额
     *
     * @param date 基准日期，为空取今天
     * @param type 统计粒度 day/week/month，为空取 day
     */
    StatRevenueVO revenue(LocalDate date, String type);

    /**
     * 服务销量排行 Top10（按销量降序，占比分母为全部服务总销量）
     */
    List<ServiceRankVO> serviceRank();

    /**
     * 美容师业绩排行（按营业额降序）
     */
    List<BeauticianRankVO> beauticianRank();

    /**
     * 用户复购率
     */
    RepurchaseVO repurchase();

}
