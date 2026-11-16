package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "格式模板请求（管理端）")
public class TemplateRequest {

    @NotBlank(message = "源扩展名不能为空")
    @Schema(description = "源扩展名", example = "docx")
    private String sourceExt;

    @NotBlank(message = "目标扩展名不能为空")
    @Schema(description = "目标扩展名", example = "pdf")
    private String targetExt;

    @NotBlank(message = "引擎不能为空")
    @Schema(description = "转换引擎：FFMPEG/LIBREOFFICE/PANDOC/IMAGEMAGICK", example = "LIBREOFFICE")
    private String engine;

    @Schema(description = "参数 Schema（JSON），前端据此动态渲染参数表单")
    private Map<String, Object> paramsSchema;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
