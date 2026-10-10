package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.dto.KnowledgeDocAddRequest;
import com.pethome.dto.KnowledgeDocPageQuery;
import com.pethome.dto.KnowledgeDocStatusRequest;
import com.pethome.dto.KnowledgeDocUpdateRequest;
import com.pethome.service.KnowledgeDocService;
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
 * 管理端 - 知识库文档管理
 *
 * <p>路径前缀 /api/admin/** 需要管理端员工 token（由 AuthInterceptor 校验）。</p>
 *
 * @author pet-home
 */
@Validated
@RestController
@RequestMapping("/api/admin/knowledge/doc")
@RequiredArgsConstructor
public class AdminKnowledgeDocController {

    private final KnowledgeDocService knowledgeDocService;

    /**
     * 分页查询，支持按分类、启用状态筛选
     */
    @GetMapping("/page")
    public Result page(@Valid KnowledgeDocPageQuery query) {
        return Result.success(knowledgeDocService.page(query));
    }

    /**
     * 文档详情（含正文）
     */
    @GetMapping("/{id}")
    public Result detail(@PathVariable @Min(value = 1, message = "文档id不合法") Long id) {
        return Result.success(knowledgeDocService.detail(id));
    }

    /**
     * 新增文档
     */
    @PostMapping("/add")
    public Result add(@RequestBody @Valid KnowledgeDocAddRequest request) {
        return Result.success(knowledgeDocService.add(request));
    }

    /**
     * 修改文档
     */
    @PutMapping("/update")
    public Result update(@RequestBody @Valid KnowledgeDocUpdateRequest request) {
        knowledgeDocService.update(request);
        return Result.success();
    }

    /**
     * 启用/禁用文档
     */
    @PutMapping("/status")
    public Result updateStatus(@RequestBody @Valid KnowledgeDocStatusRequest request) {
        knowledgeDocService.updateStatus(request.getId(), request.getEnabled());
        return Result.success();
    }

    /**
     * 重建索引（异步执行，立即返回）
     */
    @PostMapping("/reindex")
    public Result reindex() {
        knowledgeDocService.rebuildIndex();
        return Result.success();
    }

    /**
     * 查询索引状态概览
     */
    @GetMapping("/indexStatus")
    public Result indexStatus() {
        return Result.success(knowledgeDocService.indexStatus());
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable @Min(value = 1, message = "文档id不合法") Long id) {
        knowledgeDocService.delete(id);
        return Result.success();
    }

}
