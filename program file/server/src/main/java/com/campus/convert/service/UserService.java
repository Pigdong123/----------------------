package com.campus.convert.service;

import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.dto.QuotaUpdateRequest;
import com.campus.convert.domain.dto.UserAdminResponse;

/**
 * 用户管理服务（管理端）：用户、角色、启用状态、配额
 */
public interface UserService {

    PageResponse<UserAdminResponse> page(long pageNum, long pageSize, String keyword, String role);

    UserAdminResponse getById(Long userId);

    UserAdminResponse updateRole(Long userId, String role);

    UserAdminResponse updateEnabled(Long userId, boolean enabled);

    void adjustQuota(Long userId, QuotaUpdateRequest request);
}
