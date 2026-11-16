package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "提交反馈（任务反馈或通用反馈）")
public class FeedbackRequest {

    @Schema(description = "关联任务ID（通用反馈可不填）")
    private Long taskId;

    @Min(1)
    @Max(5)
    @Schema(description = "评分 1-5（任务反馈可选）", example = "5")
    private Integer rating;

    @Size(max = 30)
    @Schema(description = "分类：BUG/FEATURE/CONTENT/EXPERIENCE/OTHER", example = "BUG")
    private String category;

    @Size(max = 120)
    @Schema(description = "标题")
    private String title;

    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 1000, message = "反馈内容不能超过 1000 字")
    @Schema(description = "反馈内容")
    private String comment;

    @Size(max = 100)
    @Schema(description = "联系方式（可选）")
    private String contact;

    @Schema(description = "是否匿名")
    private Boolean anonymous;
}
