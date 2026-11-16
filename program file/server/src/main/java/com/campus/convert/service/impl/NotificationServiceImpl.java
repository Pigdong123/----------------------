package com.campus.convert.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.convert.common.BaseContext;
import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.domain.entity.Notification;
import com.campus.convert.domain.enums.NotificationTypeEnum;
import com.campus.convert.mapper.NotificationMapper;
import com.campus.convert.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 通知服务实现
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    public void notifyTaskSuccess(ConvertTask task) {
        save(task.getUserId(), task.getId(), NotificationTypeEnum.TASK_SUCCESS,
                "任务 #" + task.getId() + " 转换成功，可前往下载");
    }

    @Override
    public void notifyTaskFailed(ConvertTask task, String reason) {
        save(task.getUserId(), task.getId(), NotificationTypeEnum.TASK_FAILED,
                "任务 #" + task.getId() + " 转换失败：" + reason);
    }

    @Override
    public PageResponse<Notification> pageMine(long pageNum, long pageSize) {
        Long userId = BaseContext.getUserId();
        IPage<Notification> page = notificationMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreatedAt));
        return PageResponse.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    @Override
    public long unreadCount() {
        Long userId = BaseContext.getUserId();
        Long count = notificationMapper.selectCount(
                Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getReadFlag, 0));
        return count == null ? 0L : count;
    }

    @Override
    public void markAllRead() {
        notificationMapper.markAllRead(BaseContext.getUserId());
    }

    private void save(Long userId, Long taskId, NotificationTypeEnum type, String content) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTaskId(taskId);
        notification.setType(type.name());
        notification.setContent(content);
        notification.setReadFlag(0);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
    }
}
