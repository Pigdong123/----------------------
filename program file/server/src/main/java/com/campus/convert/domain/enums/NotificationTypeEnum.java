package com.campus.convert.domain.enums;

import lombok.Getter;

@Getter
public enum NotificationTypeEnum {

    TASK_SUCCESS("转换成功"),
    TASK_FAILED("转换失败"),
    TASK_DOWNLOADED("文件已下载"),
    SYSTEM("系统通知");

    private final String description;

    NotificationTypeEnum(String description) {
        this.description = description;
    }
}
