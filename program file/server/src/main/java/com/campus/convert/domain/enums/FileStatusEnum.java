package com.campus.convert.domain.enums;

import lombok.Getter;

@Getter
public enum FileStatusEnum {

    UPLOADED("已上传"),
    USED("已引用"),
    CONVERTED("转换产物"),
    EXPIRED("已过期"),
    DELETED("已删除");

    private final String description;

    FileStatusEnum(String description) {
        this.description = description;
    }
}
