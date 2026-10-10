package com.pethome.service;

import com.pethome.entity.Beautician;
import com.pethome.mapper.OrderCommentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 美容师评分计算服务
 *
 * <p>独立成 Bean 是为了让 {@link Async} 生效（同类内部调用不会走代理）。</p>
 *
 * @author pet-home
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BeauticianScoreService {

    private final OrderCommentMapper orderCommentMapper;
    private final BeauticianService beauticianService;

    /**
     * 异步重算并更新美容师平均评分
     */
    @Async
    public void recalculate(Long beauticianId) {
        try {
            BigDecimal average = orderCommentMapper.avgScoreByBeauticianId(beauticianId);
            if (average == null) {
                log.info("美容师暂无评价，跳过评分重算: beauticianId={}", beauticianId);
                return;
            }
            BigDecimal score = average.setScale(2, RoundingMode.HALF_UP);
            Beautician update = new Beautician();
            update.setId(beauticianId);
            update.setScore(score);
            beauticianService.updateById(update);
            log.info("重算美容师平均评分完成: beauticianId={}, average={}, score={}", beauticianId, average, score);
        } catch (Exception e) {
            // 评分重算失败不影响主流程，仅记录日志
            log.error("重算美容师平均评分失败: beauticianId={}", beauticianId, e);
        }
    }

}
