package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "格式推荐响应")
public class RecommendResponse {

    @Schema(description = "源文件ID")
    private Long fileId;

    @Schema(description = "源扩展名")
    private String sourceExt;

    @Schema(description = "推荐的目标格式列表")
    private List<String> recommendedFormats;

    @Schema(description = "推荐理由")
    private String reason;

    @Schema(description = "推荐来源：ai/rule（用于演示 AI 可降级）")
    private String provider;
}
