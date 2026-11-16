package com.campus.convert.service;

import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.entity.AuditLog;

/**
 * 审计日志服务：记录与查询（日志脱敏）
 */
public interface AuditLogService {

    void record(String action, String target, String detail);

    PageResponse<AuditLog> page(long pageNum, long pageSize, String action);
}
