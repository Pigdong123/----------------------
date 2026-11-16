package com.campus.convert.service;

import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.dto.TaskCreateRequest;
import com.campus.convert.domain.dto.TaskResponse;
import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.domain.entity.FileRecord;
import com.campus.convert.domain.enums.TaskStatusEnum;

/**
 * 转换任务服务：创建、状态机流转、重试、取消、下载产物解析
 */
public interface TaskService {

    TaskResponse create(TaskCreateRequest request);

    TaskResponse get(Long taskId);

    ConvertTask getEntity(Long taskId);

    PageResponse<TaskResponse> pageMine(long pageNum, long pageSize, String status);

    void cancel(Long taskId);

    TaskResponse retry(Long taskId);

    void updateStatus(Long taskId, TaskStatusEnum target);

    void markConverting(Long taskId);

    void markQc(Long taskId);

    void markSuccess(Long taskId, Long resultFileId);

    void markDownloaded(Long taskId);

    void failTask(Long taskId, String reason);

    FileRecord resolveResultFile(Long taskId);

    TaskResponse toResponse(ConvertTask task);

    /**
     * 将成功/已下载且过期的任务归档为 EXPIRED（由定时任务调用）
     *
     * @return 归档数量
     */
    int cleanupExpired();
}
