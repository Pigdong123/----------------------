package com.campus.convert.controller;

import com.campus.convert.common.Result;
import com.campus.convert.engine.EngineRegistry;
import com.campus.convert.queue.TaskQueue;
import com.campus.convert.service.FileService;
import com.campus.convert.service.StatsService;
import com.campus.convert.service.TaskService;
import com.campus.convert.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "运维端")
@RestController
@RequestMapping("/ops")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OPS','SYS_ADMIN')")
public class OpsController {

    private final TaskQueue taskQueue;
    private final EngineRegistry engineRegistry;
    private final StorageService storageService;
    private final StatsService statsService;
    private final FileService fileService;
    private final TaskService taskService;

    @Operation(summary = "队列监控")
    @GetMapping("/queue")
    public Result<Map<String, Object>> queue() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("queuedCount", taskQueue.queuedCount());
        map.put("snapshot", taskQueue.snapshot());
        return Result.ok(map);
    }

    @Operation(summary = "转换引擎健康检查")
    @GetMapping("/engines")
    public Result<Map<String, Object>> engines() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("healthy", engineRegistry.allEnginesHealthy());
        map.put("engines", engineRegistry.engineInfo());
        return Result.ok(map);
    }

    @Operation(summary = "存储空间监控")
    @GetMapping("/storage")
    public Result<Map<String, Object>> storage() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("usedBytes", storageService.usedStorageBytes());
        map.put("availableBytes", storageService.availableStorageBytes());
        return Result.ok(map);
    }

    @Operation(summary = "系统总览")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(statsService.overview());
    }

    @Operation(summary = "手动触发过期清理")
    @PostMapping("/maintenance/cleanup")
    public Result<Map<String, Object>> cleanup() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("expiredFiles", fileService.cleanupExpired());
        map.put("expiredTasks", taskService.cleanupExpired());
        return Result.ok(map);
    }
}
