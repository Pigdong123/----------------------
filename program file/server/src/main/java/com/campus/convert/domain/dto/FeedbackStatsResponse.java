package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Data
@Schema(description = "反馈统计")
public class FeedbackStatsResponse {

    private long total;
    private long pending;
    private long processing;
    private long resolved;
    private long closed;
    private Double avgRating;
    private Map<String, Long> byCategory;
}
