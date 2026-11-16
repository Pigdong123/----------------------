package com.campus.convert.controller;

import com.campus.convert.common.Result;
import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.dto.QuotaUpdateRequest;
import com.campus.convert.domain.dto.TemplateRequest;
import com.campus.convert.domain.dto.UserAdminResponse;
import com.campus.convert.domain.entity.AuditLog;
import com.campus.convert.domain.entity.FormatTemplate;
import com.campus.convert.service.AuditLogService;
import com.campus.convert.service.FormatTemplateService;
import com.campus.convert.service.StatsService;
import com.campus.convert.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "管理端")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SYS_ADMIN','COURSE_ADMIN')")
public class AdminController {

    private final UserService userService;
    private final FormatTemplateService formatTemplateService;
    private final StatsService statsService;
    private final AuditLogService auditLogService;

    // ---------- 用户与权限（系统管理员） ----------

    @Operation(summary = "用户列表")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @GetMapping("/users")
    public Result<PageResponse<UserAdminResponse>> users(@RequestParam(defaultValue = "1") long pageNum,
                                                         @RequestParam(defaultValue = "10") long pageSize,
                                                         @RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String role) {
        return Result.ok(userService.page(pageNum, pageSize, keyword, role));
    }

    @Operation(summary = "调整用户角色")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @PutMapping("/users/{id}/role")
    public Result<UserAdminResponse> updateRole(@PathVariable Long id, @RequestParam String role) {
        UserAdminResponse response = userService.updateRole(id, role);
        auditLogService.record("UPDATE_USER_ROLE", "user:" + id, "role=" + role);
        return Result.ok(response);
    }

    @Operation(summary = "启用/禁用用户")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @PutMapping("/users/{id}/enabled")
    public Result<UserAdminResponse> updateEnabled(@PathVariable Long id, @RequestParam boolean enabled) {
        UserAdminResponse response = userService.updateEnabled(id, enabled);
        auditLogService.record("UPDATE_USER_ENABLED", "user:" + id, "enabled=" + enabled);
        return Result.ok(response);
    }

    @Operation(summary = "调整用户配额")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @PutMapping("/users/{id}/quota")
    public Result<Void> adjustQuota(@PathVariable Long id, @Valid @RequestBody QuotaUpdateRequest request) {
        userService.adjustQuota(id, request);
        auditLogService.record("UPDATE_QUOTA", "user:" + id, null);
        return Result.ok();
    }

    // ---------- 格式模板（教务/系统管理员） ----------

    @Operation(summary = "格式模板列表")
    @GetMapping("/templates")
    public Result<List<FormatTemplate>> templates() {
        return Result.ok(formatTemplateService.listAll());
    }

    @Operation(summary = "新增格式模板")
    @PostMapping("/templates")
    public Result<FormatTemplate> createTemplate(@Valid @RequestBody TemplateRequest request) {
        FormatTemplate template = formatTemplateService.create(request);
        auditLogService.record("CREATE_TEMPLATE", "template:" + template.getId(), null);
        return Result.ok(template);
    }

    @Operation(summary = "更新格式模板")
    @PutMapping("/templates/{id}")
    public Result<FormatTemplate> updateTemplate(@PathVariable Long id,
                                                 @Valid @RequestBody TemplateRequest request) {
        FormatTemplate template = formatTemplateService.update(id, request);
        auditLogService.record("UPDATE_TEMPLATE", "template:" + id, null);
        return Result.ok(template);
    }

    @Operation(summary = "删除格式模板")
    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        formatTemplateService.delete(id);
        auditLogService.record("DELETE_TEMPLATE", "template:" + id, null);
        return Result.ok();
    }

    @Operation(summary = "启用/停用格式模板")
    @PutMapping("/templates/{id}/toggle")
    public Result<Void> toggleTemplate(@PathVariable Long id, @RequestParam boolean enabled) {
        formatTemplateService.toggle(id, enabled);
        auditLogService.record("TOGGLE_TEMPLATE", "template:" + id, "enabled=" + enabled);
        return Result.ok();
    }

    // ---------- 统计报表 ----------

    @Operation(summary = "总览统计")
    @GetMapping("/stats/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(statsService.overview());
    }

    @Operation(summary = "按格式统计")
    @GetMapping("/stats/formats")
    public Result<List<Map<String, Object>>> byFormat(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.byFormat(days));
    }

    @Operation(summary = "按状态统计")
    @GetMapping("/stats/status")
    public Result<List<Map<String, Object>>> byStatus() {
        return Result.ok(statsService.byStatus());
    }

    // ---------- 审计日志 ----------

    @Operation(summary = "审计日志")
    @PreAuthorize("hasRole('SYS_ADMIN')")
    @GetMapping("/audit")
    public Result<PageResponse<AuditLog>> audit(@RequestParam(defaultValue = "1") long pageNum,
                                                @RequestParam(defaultValue = "20") long pageSize,
                                                @RequestParam(required = false) String action) {
        return Result.ok(auditLogService.page(pageNum, pageSize, action));
    }
}
