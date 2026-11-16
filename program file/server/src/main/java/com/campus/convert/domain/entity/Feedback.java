package com.campus.convert.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("feedback")
public class Feedback implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联任务（通用反馈可为空） */
    private Long taskId;

    private Long userId;

    /** BUG / FEATURE / CONTENT / EXPERIENCE / OTHER */
    private String category;

    private String title;

    /** 评分 1-5（任务反馈可选） */
    private Integer rating;

    private String comment;

    private String contact;

    /** 1=匿名 */
    private Integer anonymous;

    /** PENDING / PROCESSING / RESOLVED / CLOSED */
    private String status;

    private String adminReply;

    private Long repliedBy;

    private LocalDateTime repliedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
