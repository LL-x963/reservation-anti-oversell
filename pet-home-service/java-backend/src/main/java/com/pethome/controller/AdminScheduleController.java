package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.dto.ScheduleBatchSetRequest;
import com.pethome.dto.ScheduleListQuery;
import com.pethome.dto.ScheduleUpdateRequest;
import com.pethome.service.BeauticianScheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 - 排班管理
 *
 * <p>路径前缀 /api/admin/** 需要管理端员工 token（由 AuthInterceptor 校验）。</p>
 *
 * @author pet-home
 */
@Validated
@RestController
@RequestMapping("/api/admin/schedule")
@RequiredArgsConstructor
public class AdminScheduleController {

    private final BeauticianScheduleService beauticianScheduleService;

    /**
     * 按美容师 id 和日期查询排班列表
     */
    @GetMapping("/list")
    public Result list(@Valid ScheduleListQuery query) {
        return Result.success(beauticianScheduleService.listByBeauticianAndDate(query));
    }

    /**
     * 批量设置排班（已存在的时段置为可接单，不存在的新增，被订单占用的跳过）
     */
    @PostMapping("/batchSet")
    public Result batchSet(@RequestBody @Valid ScheduleBatchSetRequest request) {
        return Result.success(beauticianScheduleService.batchSet(request));
    }

    /**
     * 修改单条排班可用状态
     */
    @PutMapping("/update/{id}")
    public Result update(@PathVariable @Min(value = 1, message = "排班id不合法") Long id,
                         @RequestBody @Valid ScheduleUpdateRequest request) {
        beauticianScheduleService.updateAvailable(id, request.getAvailable());
        return Result.success();
    }

    /**
     * 删除排班
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable @Min(value = 1, message = "排班id不合法") Long id) {
        beauticianScheduleService.delete(id);
        return Result.success();
    }

}
