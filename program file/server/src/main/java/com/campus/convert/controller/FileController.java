package com.campus.convert.controller;

import com.campus.convert.ai.AiService;
import com.campus.convert.common.Result;
import com.campus.convert.domain.dto.FileUploadResponse;
import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.dto.RecommendResponse;
import com.campus.convert.domain.entity.FileRecord;
import com.campus.convert.service.AuditLogService;
import com.campus.convert.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "文件")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final AiService aiService;
    private final AuditLogService auditLogService;

    @Operation(summary = "上传文件（支持拖拽、进度由前端负责）")
    @PostMapping
    public Result<FileUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        FileUploadResponse response = fileService.upload(file);
        auditLogService.record("UPLOAD_FILE", "file:" + response.getFileId(), null);
        return Result.ok(response);
    }

    @Operation(summary = "我的文件列表")
    @GetMapping
    public Result<PageResponse<FileRecord>> page(@RequestParam(defaultValue = "1") long pageNum,
                                                 @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(fileService.pageMine(pageNum, pageSize));
    }

    @Operation(summary = "文件详情")
    @GetMapping("/{id}")
    public Result<FileRecord> get(@PathVariable Long id) {
        return Result.ok(fileService.getOwned(id));
    }

    @Operation(summary = "删除我的文件")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.deleteOwn(id);
        auditLogService.record("DELETE_FILE", "file:" + id, null);
        return Result.ok();
    }

    @Operation(summary = "格式推荐（AI 增强，可降级）")
    @PostMapping("/{id}/recommend")
    public Result<RecommendResponse> recommend(@PathVariable Long id) {
        fileService.getOwned(id);
        return Result.ok(aiService.recommend(id));
    }
}
