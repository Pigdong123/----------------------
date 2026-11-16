package com.campus.convert.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.convert.common.BaseContext;
import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import com.campus.convert.domain.dto.LoginRequest;
import com.campus.convert.domain.dto.LoginResponse;
import com.campus.convert.domain.dto.RegisterRequest;
import com.campus.convert.domain.entity.User;
import com.campus.convert.domain.enums.RoleEnum;
import com.campus.convert.mapper.UserMapper;
import com.campus.convert.security.JwtUtil;
import com.campus.convert.security.LoginUser;
import com.campus.convert.service.AuthService;
import com.campus.convert.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 认证服务实现
 * <p>
 * 说明：系统第一个注册用户自动成为系统管理员（SYS_ADMIN），便于课程设计演示时完成初始化。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final QuotaService quotaService;

    /** 单实例内串行化注册，避免并发下"首个用户"被重复判定为 SYS_ADMIN */
    private static final Object REGISTER_LOCK = new Object();

    @Value("${campus.quota.default-daily-limit:20}")
    private int defaultDailyLimit;

    @Override
    @Transactional
    public LoginUser register(RegisterRequest request) {
        synchronized (REGISTER_LOCK) {
            Long exists = userMapper.selectCount(
                    Wrappers.<User>lambdaQuery().eq(User::getUsername, request.getUsername()));
            if (exists != null && exists > 0) {
                throw new BizException(ResultCode.BAD_REQUEST, "用户名已存在");
            }
            boolean firstUser = userMapper.selectCount(null) == 0;
            // 关键修复：自助注册只允许 STUDENT / TEACHER，禁止通过 role 参数提权到管理员
            String role = firstUser ? RoleEnum.SYS_ADMIN.name() : resolveSelfRegistrationRole(request.getRole());

            User user = new User();
            user.setUsername(request.getUsername());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setRole(role);
            user.setQuota(defaultDailyLimit);
            user.setEnabled(1);
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            user.setDeleted(0);
            try {
                userMapper.insert(user);
            } catch (DuplicateKeyException e) {
                throw new BizException(ResultCode.BAD_REQUEST, "用户名已存在");
            }

            quotaService.getOrInit(user.getId());
            return toLoginUser(user);
        }
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery().eq(User::getUsername, request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (user.getEnabled() != null && user.getEnabled() == 0) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        LoginUser loginUser = toLoginUser(user);
        loginUser.setToken(jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole()));

        LoginResponse response = new LoginResponse();
        response.setToken(loginUser.getToken());
        response.setUser(loginUser);
        return response;
    }

    @Override
    public void logout() {
        // JWT 无状态：服务端仅清理线程上下文，客户端丢弃令牌即可
        BaseContext.clear();
    }

    @Override
    public LoginUser currentUser() {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return toLoginUser(user);
    }

    private String resolveSelfRegistrationRole(String role) {
        if (role == null || role.isBlank()) {
            return RoleEnum.STUDENT.name();
        }
        try {
            RoleEnum parsed = RoleEnum.valueOf(role.trim().toUpperCase(Locale.ROOT));
            if (parsed == RoleEnum.STUDENT || parsed == RoleEnum.TEACHER) {
                return parsed.name();
            }
        } catch (IllegalArgumentException ignored) {
            // fall through to rejection
        }
        throw new BizException(ResultCode.FORBIDDEN, "自助注册仅支持 STUDENT 或 TEACHER 角色");
    }

    private LoginUser toLoginUser(User user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRole(user.getRole());
        return loginUser;
    }
}
