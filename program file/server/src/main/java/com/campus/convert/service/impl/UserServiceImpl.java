package com.campus.convert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.dto.QuotaUpdateRequest;
import com.campus.convert.domain.dto.UserAdminResponse;
import com.campus.convert.domain.entity.Quota;
import com.campus.convert.domain.entity.User;
import com.campus.convert.mapper.QuotaMapper;
import com.campus.convert.mapper.UserMapper;
import com.campus.convert.service.QuotaService;
import com.campus.convert.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 用户管理服务实现（管理端）
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserMapper userMapper;
    private final QuotaMapper quotaMapper;
    private final QuotaService quotaService;

    @Override
    public PageResponse<UserAdminResponse> page(long pageNum, long pageSize, String keyword, String role) {
        LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery()
                .like(StringUtils.hasText(keyword), User::getUsername, keyword)
                .eq(StringUtils.hasText(role), User::getRole, role)
                .orderByDesc(User::getCreatedAt);
        IPage<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<UserAdminResponse> records = page.getRecords().stream().map(this::toResponse).toList();
        return PageResponse.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    @Override
    public UserAdminResponse getById(Long userId) {
        return toResponse(requireUser(userId));
    }

    @Override
    public UserAdminResponse updateRole(Long userId, String role) {
        User user = requireUser(userId);
        try {
            user.setRole(com.campus.convert.domain.enums.RoleEnum.valueOf(role.toUpperCase()).name());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "非法角色: " + role);
        }
        userMapper.updateById(user);
        return toResponse(user);
    }

    @Override
    public UserAdminResponse updateEnabled(Long userId, boolean enabled) {
        User user = requireUser(userId);
        user.setEnabled(enabled ? 1 : 0);
        userMapper.updateById(user);
        return toResponse(user);
    }

    @Override
    public void adjustQuota(Long userId, QuotaUpdateRequest request) {
        requireUser(userId);
        Quota quota = quotaService.getOrInit(userId);
        if (request.getDailyLimit() != null) {
            quota.setDailyLimit(request.getDailyLimit());
        }
        if (request.getStorageLimit() != null) {
            quota.setStorageLimit(request.getStorageLimit());
        }
        quotaMapper.updateById(quota);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private UserAdminResponse toResponse(User user) {
        UserAdminResponse response = new UserAdminResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setRole(user.getRole());
        response.setQuota(user.getQuota());
        response.setEnabled(user.getEnabled());
        response.setCreatedAt(user.getCreatedAt() == null ? null : user.getCreatedAt().format(DATE_TIME));
        return response;
    }
}
