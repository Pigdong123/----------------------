package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户管理响应（管理端）")
public class UserAdminResponse {

    @Schema(description = "用户ID")
    private Long id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "角色")
    private String role;

    @Schema(description = "每日配额")
    private Integer quota;

    @Schema(description = "是否启用")
    private Integer enabled;

    @Schema(description = "注册时间")
    private String createdAt;
}
