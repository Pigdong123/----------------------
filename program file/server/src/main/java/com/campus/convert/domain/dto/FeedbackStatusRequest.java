package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "修改反馈处理状态")
public class FeedbackStatusRequest {

    @NotBlank(message = "状态不能为空")
    @Schema(description = "PENDING/PROCESSING/RESOLVED/CLOSED", example = "PROCESSING")
    private String status;
}
