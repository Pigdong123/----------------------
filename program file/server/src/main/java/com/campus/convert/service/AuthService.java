package com.campus.convert.service;

import com.campus.convert.domain.dto.LoginRequest;
import com.campus.convert.domain.dto.LoginResponse;
import com.campus.convert.domain.dto.RegisterRequest;
import com.campus.convert.security.LoginUser;

/**
 * 认证服务：注册 / 登录 / 退出 / 当前用户
 */
public interface AuthService {

    LoginUser register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    void logout();

    LoginUser currentUser();
}
