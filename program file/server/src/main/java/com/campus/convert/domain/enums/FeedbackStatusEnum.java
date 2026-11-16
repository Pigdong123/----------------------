package com.campus.convert.domain.enums;

import lombok.Getter;

import java.util.Locale;

/**
 * 反馈处理状态
 */
@Getter
public enum FeedbackStatusEnum {

    PENDING("待处理"),
    PROCESSING("处理中"),
    RESOLVED("已解决"),
    CLOSED("已关闭");

    private final String description;

    FeedbackStatusEnum(String description) {
        this.description = description;
    }

    public static FeedbackStatusEnum from(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("非法反馈状态: " + value);
        }
    }
}
