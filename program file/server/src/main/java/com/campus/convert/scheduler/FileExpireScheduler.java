package com.campus.convert.scheduler;

import com.campus.convert.service.FileService;
import com.campus.convert.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 过期清理调度器：定时清理过期文件与归档过期任务（对应需求"文件过期自动清理"）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileExpireScheduler {

    private final FileService fileService;
    private final TaskService taskService;

    @Scheduled(cron = "${campus.file.cleanup-cron:0 30 3 * * ?}")
    public void cleanup() {
        int files = fileService.cleanupExpired();
        int tasks = taskService.cleanupExpired();
        if (files > 0 || tasks > 0) {
            log.info("过期清理完成: files={}, tasks={}", files, tasks);
        }
    }
}
