package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "任务响应")
public class TaskResponse {

    @Schema(description = "任务ID")
    private Long id;

    @Schema(description = "源文件ID")
    private Long sourceFileId;

    @Schema(description = "源文件名")
    private String sourceFileName;

    @Schema(description = "目标格式")
    private String targetFormat;

    @Schema(description = "转换参数")
    private Object params;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "状态描述")
    private String statusDesc;

    @Schema(description = "优先级")
    private Integer priority;

    @Schema(description = "失败原因（用户可读）")
    private String errorMsg;

    @Schema(description = "已重试次数")
    private Integer retryCount;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "完成时间")
    private LocalDateTime finishedAt;

    @Schema(description = "下载令牌（仅成功状态返回）")
    private String downloadToken;

    @Schema(description = "AI 诊断信息（可选）")
    private String diagnosis;
}
