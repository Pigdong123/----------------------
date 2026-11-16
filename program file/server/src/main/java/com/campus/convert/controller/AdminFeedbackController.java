package com.campus.convert.controller;

import com.campus.convert.common.Result;
import com.campus.convert.domain.dto.FeedbackQuery;
import com.campus.convert.domain.dto.FeedbackReplyRequest;
import com.campus.convert.domain.dto.FeedbackResponse;
import com.campus.convert.domain.dto.FeedbackStatsResponse;
import com.campus.convert.domain.dto.FeedbackStatusRequest;
import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.service.FeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端反馈接口：列表/筛选/详情/回复/状态/统计。
 */
@Tag(name = "管理端-反馈")
@RestController
@RequestMapping("/admin/feedback")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SYS_ADMIN','COURSE_ADMIN','OPS')")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    @Operation(summary = "反馈列表（支持状态/分类/评分/关键词筛选）")
    @GetMapping
    public Result<PageResponse<FeedbackResponse>> list(@ModelAttribute FeedbackQuery query) {
        return Result.ok(feedbackService.pageAdmin(query));
    }

    @Operation(summary = "反馈统计")
    @GetMapping("/stats")
    public Result<FeedbackStatsResponse> stats() {
        return Result.ok(feedbackService.stats());
    }

    @Operation(summary = "反馈详情")
    @GetMapping("/{id}")
    public Result<FeedbackResponse> detail(@PathVariable Long id) {
        return Result.ok(feedbackService.getAdmin(id));
    }

    @Operation(summary = "回复反馈")
    @PostMapping("/{id}/reply")
    public Result<FeedbackResponse> reply(@PathVariable Long id,
                                          @Valid @RequestBody FeedbackReplyRequest request) {
        return Result.ok(feedbackService.reply(id, request));
    }

    @Operation(summary = "修改反馈状态")
    @PutMapping("/{id}/status")
    public Result<FeedbackResponse> updateStatus(@PathVariable Long id,
                                                 @Valid @RequestBody FeedbackStatusRequest request) {
        return Result.ok(feedbackService.updateStatus(id, request));
    }
}
