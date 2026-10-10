package com.pethome.service.impl;

import com.pethome.common.enums.OrderStatus;
import com.pethome.common.enums.StatType;
import com.pethome.common.exception.BusinessException;
import com.pethome.mapper.StatMapper;
import com.pethome.service.StatService;
import com.pethome.vo.BeauticianRankVO;
import com.pethome.vo.RepurchaseVO;
import com.pethome.vo.ServiceRankVO;
import com.pethome.vo.StatRevenueItemVO;
import com.pethome.vo.StatRevenueVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据统计服务实现
 *
 * <p>统计口径：仅计入「已完成」订单（5已完成 + 7待评价，见 {@link OrderStatus#finishedCodes()}）；
 * 营业额按下单支付时间 pay_time 归入自然日。</p>
 *
 * @author pet-home
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatServiceImpl implements StatService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    /** 占比保留小数位 */
    private static final int RATIO_SCALE = 4;
    /** 服务销量排行条数 */
    private static final int SERVICE_RANK_LIMIT = 10;

    private final StatMapper statMapper;

    @Override
    public StatRevenueVO revenue(LocalDate date, String type) {
        LocalDate baseDate = date == null ? LocalDate.now() : date;
        StatType statType = parseStatType(type);

        // 1. 按统计粒度确定自然区间
        LocalDate startDate;
        LocalDate endDate;
        switch (statType) {
            case WEEK -> {
                // 自然周：周一至周日
                startDate = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                endDate = startDate.plusDays(6);
            }
            case MONTH -> {
                // 自然月
                startDate = baseDate.withDayOfMonth(1);
                endDate = startDate.plusMonths(1).minusDays(1);
            }
            default -> {
                startDate = baseDate;
                endDate = baseDate;
            }
        }

        // 2. 聚合查询区间内每日营业额
        List<StatRevenueItemVO> items = statMapper.selectRevenueByDay(
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay(),
                OrderStatus.finishedCodes());
        Map<LocalDate, BigDecimal> amountMap = new HashMap<>(items.size());
        for (StatRevenueItemVO item : items) {
            if (item.getStatDate() != null) {
                amountMap.put(item.getStatDate(), item.getAmount() == null ? ZERO : item.getAmount());
            }
        }

        // 3. 逐日补全（无数据日期补 0），并汇总总额
        List<LocalDate> dates = new ArrayList<>();
        List<BigDecimal> revenues = new ArrayList<>();
        BigDecimal totalAmount = ZERO;
        for (LocalDate current = startDate; !current.isAfter(endDate); current = current.plusDays(1)) {
            BigDecimal amount = amountMap.getOrDefault(current, ZERO);
            dates.add(current);
            revenues.add(amount);
            totalAmount = totalAmount.add(amount);
        }

        log.info("营业额统计: type={}, date={}, 区间=[{}, {}], 总额={}",
                statType.getValue(), baseDate, startDate, endDate, totalAmount);
        return new StatRevenueVO(dates, revenues, totalAmount);
    }

    @Override
    public List<ServiceRankVO> serviceRank() {
        List<Integer> statuses = OrderStatus.finishedCodes();
        List<ServiceRankVO> ranks = statMapper.selectServiceRank(statuses, SERVICE_RANK_LIMIT);
        BigDecimal totalSales = statMapper.selectTotalSales(statuses);
        ranks.forEach(rank -> rank.setRatio(ratio(rank.getSales(), totalSales)));
        return ranks;
    }

    @Override
    public List<BeauticianRankVO> beauticianRank() {
        return statMapper.selectBeauticianRank(OrderStatus.finishedCodes()).stream()
                .peek(rank -> {
                    if (rank.getScore() != null) {
                        rank.setScore(rank.getScore().setScale(2, RoundingMode.HALF_UP));
                    }
                })
                .collect(Collectors.toList());
    }

    @Override
    public RepurchaseVO repurchase() {
        RepurchaseVO result = statMapper.selectRepurchase(OrderStatus.finishedCodes());
        if (result == null) {
            result = new RepurchaseVO(0L, 0L, null);
        }
        if (result.getTotalUsers() == null) {
            result.setTotalUsers(0L);
        }
        if (result.getRepeatUsers() == null) {
            result.setRepeatUsers(0L);
        }
        result.setRepurchaseRate(ratio(result.getRepeatUsers(), BigDecimal.valueOf(result.getTotalUsers())));
        return result;
    }

    private StatType parseStatType(String type) {
        // 不传统计粒度时默认按自然日
        if (type == null || type.isBlank()) {
            return StatType.DAY;
        }
        try {
            return StatType.fromValue(type);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("统计粒度只能为 " + StatType.allValuesAsText());
        }
    }

    /**
     * 计算占比（0~1，保留 4 位小数），分母为 0 时返回 0
     */
    private BigDecimal ratio(Number part, BigDecimal total) {
        if (part == null || total == null || total.compareTo(ZERO) == 0) {
            return ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(part.longValue()).divide(total, RATIO_SCALE, RoundingMode.HALF_UP);
    }

}
