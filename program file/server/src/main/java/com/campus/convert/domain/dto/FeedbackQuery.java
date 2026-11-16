package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "反馈分页查询条件")
public class FeedbackQuery {

    @Schema(description = "页码", example = "1")
    private long pageNum = 1;

    @Schema(description = "每页条数", example = "10")
    private long pageSize = 10;

    @Schema(description = "状态过滤")
    private String status;

    @Schema(description = "分类过滤")
    private String category;

    @Schema(description = "关键词（标题/内容）")
    private String keyword;

    @Schema(description = "评分过滤")
    private Integer rating;

    @Schema(description = "任务ID过滤")
    private Long taskId;
}
