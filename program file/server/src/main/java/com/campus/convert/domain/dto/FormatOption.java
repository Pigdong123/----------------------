package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "单个受支持的格式转换能力")
public class FormatOption {

    @Schema(description = "源格式", example = "m4s")
    private String sourceExt;

    @Schema(description = "目标格式", example = "mp4")
    private String targetExt;

    @Schema(description = "转换引擎", example = "FFMPEG")
    private String engine;

    @Schema(description = "能力分类：document/audio/image/video/subtitle/ocr")
    private String category;

    @Schema(description = "参数 Schema（前端动态渲染）")
    private Map<String, Object> paramsSchema;

    @Schema(description = "当前环境该能力是否可用（引擎是否存在并支持）")
    private boolean available;
}
