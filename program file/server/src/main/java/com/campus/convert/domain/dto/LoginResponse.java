package com.campus.convert.domain.dto;

import com.campus.convert.security.LoginUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "令牌")
    private String token;

    @Schema(description = "用户信息")
    private LoginUser user;
}
