package com.campus.convert.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "反馈详情")
public class FeedbackResponse {

    private Long id;
    private Long taskId;
    private Long userId;
    private String username;
    private String category;
    private String title;
    private Integer rating;
    private String comment;
    private String contact;
    private Boolean anonymous;
    private String status;
    private String statusDesc;
    private String adminReply;
    private Long repliedBy;
    private LocalDateTime repliedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
