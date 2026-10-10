package com.pethome.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量设置排班结果
 *
 * @author pet-home
 */
@Data
public class ScheduleBatchSetVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long beauticianId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    /** 去重后的有效时段总数 */
    private Integer total;

    /** 新增数量 */
    private Integer addedCount;

    /** 更新为可接单数量 */
    private Integer updatedCount;

    /** 无需变更（已是可接单）数量 */
    private Integer unchangedCount;

    /** 因被订单占用而跳过数量 */
    private Integer skippedCount;

    /** 新增的时段 */
    private List<String> addedSlots = new ArrayList<>();

    /** 更新为可接单的时段 */
    private List<String> updatedSlots = new ArrayList<>();

    /** 无需变更的时段 */
    private List<String> unchangedSlots = new ArrayList<>();

    /** 被跳过的时段及原因 */
    private List<ScheduleSkippedVO> skippedSlots = new ArrayList<>();

}
