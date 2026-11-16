package com.campus.convert.domain.enums;

import lombok.Getter;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * 任务状态机（对应需求文档第 9 节）
 * <p>
 * 待校验 → 校验失败
 * 待校验 → 排队中 → 转换中 → 质检中 → 成功 → 已下载 → 已过期 → 已删除
 * 转换中 → 失败 → 可重试 → 排队中
 * 排队中 → 已取消
 */
@Getter
public enum TaskStatusEnum {

    PENDING_VALIDATE("待校验"),
    VALIDATION_FAILED("校验失败"),
    QUEUED("排队中"),
    CONVERTING("转换中"),
    QC("质检中"),
    SUCCESS("成功"),
    FAILED("失败"),
    RETRYABLE("可重试"),
    DOWNLOADED("已下载"),
    EXPIRED("已过期"),
    DELETED("已删除"),
    CANCELLED("已取消");

    private final String description;

    private static final Map<TaskStatusEnum, Set<TaskStatusEnum>> TRANSITIONS = new EnumMap<>(TaskStatusEnum.class);

    static {
        TRANSITIONS.put(PENDING_VALIDATE, Set.of(VALIDATION_FAILED, QUEUED));
        TRANSITIONS.put(QUEUED, Set.of(CONVERTING, CANCELLED));
        TRANSITIONS.put(CONVERTING, Set.of(QC, FAILED));
        TRANSITIONS.put(QC, Set.of(SUCCESS, FAILED));
        TRANSITIONS.put(FAILED, Set.of(RETRYABLE));
        TRANSITIONS.put(RETRYABLE, Set.of(QUEUED));
        TRANSITIONS.put(SUCCESS, Set.of(DOWNLOADED, EXPIRED));
        TRANSITIONS.put(DOWNLOADED, Set.of(EXPIRED));
        TRANSITIONS.put(EXPIRED, Set.of(DELETED));
    }

    TaskStatusEnum(String description) {
        this.description = description;
    }

    /**
     * 校验状态流转是否合法
     */
    public boolean canTransitTo(TaskStatusEnum target) {
        Set<TaskStatusEnum> allowed = TRANSITIONS.get(this);
        return allowed != null && allowed.contains(target);
    }

    public void assertTransitTo(TaskStatusEnum target) {
        if (!canTransitTo(target)) {
            throw new IllegalStateException("非法状态流转: " + this + " -> " + target);
        }
    }
}
