package com.campus.convert.controller;

import com.campus.convert.common.Result;
import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.entity.Notification;
import com.campus.convert.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "通知")
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "我的通知列表")
    @GetMapping
    public Result<PageResponse<Notification>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(notificationService.pageMine(pageNum, pageSize));
    }

    @Operation(summary = "未读数量")
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(notificationService.unreadCount());
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead();
        return Result.ok();
    }
}
