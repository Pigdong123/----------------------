package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理员回复反馈")
public class FeedbackReplyRequest {

    @NotBlank(message = "回复内容不能为空")
    @Size(max = 1000, message = "回复内容不能超过 1000 字")
    @Schema(description = "回复内容")
    private String reply;

    @Schema(description = "回复后状态：PROCESSING/RESOLVED/CLOSED，默认 RESOLVED", example = "RESOLVED")
    private String status;
}
