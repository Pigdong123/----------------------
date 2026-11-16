package com.campus.convert.security;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "登录用户信息")
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "角色：STUDENT/TEACHER/COURSE_ADMIN/OPS/SYS_ADMIN")
    private String role;

    @Schema(description = "JWT 令牌")
    private String token;
}
