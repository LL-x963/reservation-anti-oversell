package com.pethome.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.pethome.dto.ScheduleBatchSetRequest;
import com.pethome.dto.ScheduleListQuery;
import com.pethome.entity.BeauticianSchedule;
import com.pethome.vo.ScheduleBatchSetVO;
import com.pethome.vo.ScheduleVO;

import java.util.List;

/**
 * 美容师排班服务（管理端）
 *
 * @author pet-home
 */
public interface BeauticianScheduleService extends IService<BeauticianSchedule> {

    /**
     * 按美容师 id 与日期查询排班列表，按时间先后排序
     */
    List<ScheduleVO> listByBeauticianAndDate(ScheduleListQuery query);

    /**
     * 批量设置排班（增量式）：已存在的时段置为可接单，不存在的新增，
     * 已被订单占用的时段跳过并在结果中返回原因
     */
    ScheduleBatchSetVO batchSet(ScheduleBatchSetRequest request);

    /**
     * 修改单条排班的可用状态，已被订单占用的排班禁止直接设为可接单
     */
    void updateAvailable(Long id, Integer available);

    /**
     * 删除排班，已被订单占用的排班禁止删除
     */
    void delete(Long id);

}
