package com.campus.convert.common;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "权限不足"),
    NOT_FOUND(404, "资源不存在"),
    QUOTA_EXCEEDED(1001, "今日转换配额已用完"),
    FILE_TOO_LARGE(1002, "文件超过大小限制"),
    FORMAT_NOT_SUPPORTED(1003, "不支持的格式或格式组合"),
    FILE_CORRUPTED(1004, "文件损坏或无法读取"),
    FILE_EXPIRED(1005, "文件已过期"),
    DOWNLOAD_FORBIDDEN(1006, "无权下载该文件"),
    TASK_NOT_FOUND(1007, "任务不存在"),
    TASK_STATUS_INVALID(1008, "当前任务状态不允许该操作"),
    TASK_CONVERT_FAILED(1009, "转换失败"),
    STORAGE_FULL(1010, "存储空间不足"),
    RATE_LIMITED(1011, "操作过于频繁，请稍后再试"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
