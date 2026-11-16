package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "创建转换任务请求")
public class TaskCreateRequest {

    @NotNull(message = "源文件ID不能为空")
    @Schema(description = "源文件ID")
    private Long sourceFileId;

    @NotBlank(message = "目标格式不能为空")
    @Schema(description = "目标格式，如 pdf/mp3/webp", example = "pdf")
    private String targetFormat;

    @Schema(description = "转换参数，如 {\"bitrate\":\"192k\"}，需匹配格式模板的 paramsSchema")
    private Map<String, Object> params;
}
