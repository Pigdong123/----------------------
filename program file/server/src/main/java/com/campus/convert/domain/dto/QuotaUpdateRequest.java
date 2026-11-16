package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "配额调整请求（管理端）")
public class QuotaUpdateRequest {

    @Min(1)
    @Schema(description = "每日次数上限", example = "50")
    private Integer dailyLimit;

    @Min(1)
    @Schema(description = "总存储上限（字节）", example = "2147483648")
    private Long storageLimit;
}
