package com.campus.convert.domain.enums;

import java.util.Locale;

/**
 * 反馈分类
 */
public enum FeedbackCategoryEnum {

    BUG,
    FEATURE,
    CONTENT,
    EXPERIENCE,
    OTHER;

    public static FeedbackCategoryEnum from(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
