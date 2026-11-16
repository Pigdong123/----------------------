package com.campus.convert.service;

import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.domain.entity.Notification;

/**
 * 通知服务：任务成功/失败通知与已读管理
 */
public interface NotificationService {

    void notifyTaskSuccess(ConvertTask task);

    void notifyTaskFailed(ConvertTask task, String reason);

    PageResponse<Notification> pageMine(long pageNum, long pageSize);

    long unreadCount();

    void markAllRead();
}
