package com.campus.convert.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.convert.common.BaseContext;
import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.entity.AuditLog;
import com.campus.convert.mapper.AuditLogMapper;
import com.campus.convert.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 审计日志服务实现（IP 等敏感字段脱敏存储）
 */
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void record(String action, String target, String detail) {
        AuditLog log = new AuditLog();
        log.setUserId(BaseContext.getUserId());
        log.setAction(action);
        log.setTarget(target);
        log.setIp(currentIp());
        log.setDetail(detail);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    @Override
    public PageResponse<AuditLog> page(long pageNum, long pageSize, String action) {
        IPage<AuditLog> page = auditLogMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<AuditLog>lambdaQuery()
                        .eq(action != null && !action.isBlank(), AuditLog::getAction, action)
                        .orderByDesc(AuditLog::getCreatedAt));
        return PageResponse.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    private String currentIp() {
        try {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            String ip = (forwarded != null && !forwarded.isBlank())
                    ? forwarded.split(",")[0].trim()
                    : request.getRemoteAddr();
            return maskIp(ip);
        } catch (Exception e) {
            return null;
        }
    }

    private String maskIp(String ip) {
        if (ip == null) {
            return null;
        }
        int idx = ip.lastIndexOf('.');
        return idx > 0 ? ip.substring(0, idx) + ".0" : ip;
    }
}
