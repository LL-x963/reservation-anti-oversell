package com.pethome.controller;

import com.pethome.common.Result;
import com.pethome.dto.AiConversationPageQuery;
import com.pethome.dto.KnowledgeAskRequest;
import com.pethome.service.KnowledgeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端 - 知识库问答
 *
 * <p>路径前缀 /api/user/** 需要 C 端用户 token（由 AuthInterceptor 校验），
 * 问答历史一律限定在当前登录用户范围内。</p>
 *
 * @author pet-home
 */
@Validated
@RestController
@RequestMapping("/api/user/knowledge")
@RequiredArgsConstructor
public class UserKnowledgeController {

    private final KnowledgeService knowledgeService;

    /**
     * 知识库问答
     *
     * <p>RAG 服务不可用时返回 {@code code=0, bizCode=600}。</p>
     */
    @PostMapping("/ask")
    public Result ask(@RequestBody @Valid KnowledgeAskRequest request) {
        return Result.success(knowledgeService.ask(request));
    }

    /**
     * 问答历史分页
     */
    @GetMapping("/history/page")
    public Result historyPage(@Valid AiConversationPageQuery query) {
        return Result.success(knowledgeService.pageHistory(query));
    }

    /**
     * 删除单条问答历史
     */
    @DeleteMapping("/history/{id}")
    public Result deleteHistory(@PathVariable @Min(value = 1, message = "记录id不合法") Long id) {
        knowledgeService.deleteHistory(id);
        return Result.success();
    }

    /**
     * 清空问答历史
     */
    @DeleteMapping("/history")
    public Result clearHistory() {
        knowledgeService.clearHistory();
        return Result.success();
    }

}
