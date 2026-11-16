package com.campus.convert.controller;

import com.campus.convert.common.Result;
import com.campus.convert.domain.dto.FeedbackQuery;
import com.campus.convert.domain.dto.FeedbackRequest;
import com.campus.convert.domain.dto.FeedbackResponse;
import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户端反馈接口：提交/查询/撤回自己的反馈。
 */
@Tag(name = "反馈")
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "提交反馈（任务反馈或通用反馈）")
    @PostMapping
    public Result<FeedbackResponse> submit(@Valid @RequestBody FeedbackRequest request) {
        return Result.ok(feedbackService.submit(request));
    }

    @Operation(summary = "我的反馈列表")
    @GetMapping("/mine")
    public Result<PageResponse<FeedbackResponse>> mine(@ModelAttribute FeedbackQuery query) {
        return Result.ok(feedbackService.pageMine(query));
    }

    @Operation(summary = "查看我的反馈详情（含管理员回复）")
    @GetMapping("/{id}")
    public Result<FeedbackResponse> get(@PathVariable Long id) {
        return Result.ok(feedbackService.getMine(id));
    }

    @Operation(summary = "撤回待处理的反馈")
    @PostMapping("/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable Long id) {
        feedbackService.withdraw(id);
        return Result.ok();
    }

    @Operation(summary = "查看某任务的反馈（仅本人或管理/运维）")
    @GetMapping("/task/{taskId}")
    public Result<PageResponse<FeedbackResponse>> pageByTask(
            @PathVariable Long taskId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(feedbackService.pageByTask(taskId, pageNum, pageSize));
    }
}
