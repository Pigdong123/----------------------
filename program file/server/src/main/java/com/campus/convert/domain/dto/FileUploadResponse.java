package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文件上传响应")
public class FileUploadResponse {

    @Schema(description = "文件ID")
    private Long fileId;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "文件扩展名")
    private String ext;

    @Schema(description = "文件大小（字节）")
    private Long size;

    @Schema(description = "系统推荐的目标格式列表（AI 增强，可降级为规则推荐）")
    private java.util.List<String> recommendedFormats;

    @Schema(description = "剩余每日配额")
    private Integer remainingQuota;
}
