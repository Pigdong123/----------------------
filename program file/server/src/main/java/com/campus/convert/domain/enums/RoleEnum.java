package com.campus.convert.domain.enums;

import lombok.Getter;

@Getter
public enum RoleEnum {

    STUDENT("学生"),
    TEACHER("教师/助教"),
    COURSE_ADMIN("课程管理员/教务"),
    OPS("系统运维"),
    SYS_ADMIN("系统管理员");

    private final String description;

    RoleEnum(String description) {
        this.description = description;
    }
}
