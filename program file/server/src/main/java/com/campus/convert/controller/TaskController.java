package com.campus.convert.controller;

import com.campus.convert.common.BaseContext;
import com.campus.convert.common.BizException;
import com.campus.convert.common.Result;
import com.campus.convert.common.ResultCode;
import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.dto.TaskCreateRequest;
import com.campus.convert.domain.dto.TaskResponse;
import com.campus.convert.domain.entity.FileRecord;
import com.campus.convert.security.DownloadTokenStore;
import com.campus.convert.security.JwtUtil;
import com.campus.convert.service.AuditLogService;
import com.campus.convert.service.TaskService;
import com.campus.convert.storage.StorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Tag(name = "转换任务")
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final StorageService storageService;
    private final AuditLogService auditLogService;
    private final JwtUtil jwtUtil;
    private final DownloadTokenStore downloadTokenStore;

    @Value("${campus.storage.download-token-ttl-minutes:10}")
    private long downloadTokenTtlMinutes;

    @Operation(summary = "创建转换任务")
    @PostMapping
    public Result<TaskResponse> create(@Valid @RequestBody TaskCreateRequest request) {
        TaskResponse response = taskService.create(request);
        auditLogService.record("CREATE_TASK", "task:" + response.getId(), null);
        return Result.ok(response);
    }

    @Operation(summary = "我的任务列表")
    @GetMapping
    public Result<PageResponse<TaskResponse>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                   @RequestParam(defaultValue = "10") long pageSize,
                                                   @RequestParam(required = false) String status) {
        return Result.ok(taskService.pageMine(pageNum, pageSize, status));
    }

    @Operation(summary = "任务详情（含状态、诊断、下载令牌）")
    @GetMapping("/{id}")
    public Result<TaskResponse> get(@PathVariable Long id) {
        return Result.ok(taskService.get(id));
    }

    @Operation(summary = "取消排队中的任务")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        taskService.cancel(id);
        return Result.ok();
    }

    @Operation(summary = "重试失败的任务")
    @PostMapping("/{id}/retry")
    public Result<TaskResponse> retry(@PathVariable Long id) {
        return Result.ok(taskService.retry(id));
    }

    /**
     * 下载转换结果：
     * 1. 未携带 token：要求登录，并校验任务归属（原有行为）；
     * 2. 携带 token：校验令牌绑定的用户/产物，并通过 Redis 保证一次性使用。
     */
    @Operation(summary = "下载转换结果（登录态或一次性下载令牌）")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id,
                                             @RequestParam(required = false) String token) {
        FileRecord file;
        if (StringUtils.hasText(token)) {
            file = taskService.resolveResultFileByToken(id, token);
            JwtUtil.DownloadToken claims = jwtUtil.parseDownloadToken(token);
            if (!downloadTokenStore.consume(claims.jti(), downloadTokenTtlMinutes)) {
                throw new BizException(ResultCode.DOWNLOAD_FORBIDDEN, "下载令牌已使用或已失效");
            }
        } else {
            if (BaseContext.getUserId() == null) {
                throw new BizException(ResultCode.UNAUTHORIZED, "请登录或使用下载令牌");
            }
            file = taskService.resolveResultFile(id);
        }

        InputStream inputStream = storageService.load(file.getStoragePath());
        taskService.markDownloaded(id);
        auditLogService.record("DOWNLOAD_TASK", "task:" + id, null);

        String filename = URLEncoder.encode(file.getOriginalName(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(inputStream));
    }
}
