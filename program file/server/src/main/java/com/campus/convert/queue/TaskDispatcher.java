package com.campus.convert.queue;

import com.campus.convert.common.BizException;
import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.domain.entity.FileRecord;
import com.campus.convert.domain.enums.TaskStatusEnum;
import com.campus.convert.engine.EngineRegistry;
import com.campus.convert.engine.qc.QcChecker;
import com.campus.convert.service.FileService;
import com.campus.convert.service.NotificationService;
import com.campus.convert.service.TaskService;
import com.campus.convert.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 任务调度器：从队列取出任务并驱动 转换 → 质检 → 登记产物 → 通知 全流程
 * <p>
 * 状态流转：QUEUED → CONVERTING → QC → SUCCESS / FAILED
 * 触发入口：{@code scheduler.TaskPollScheduler} 轮询调用
 * <p>
 * 审计修复：
 * 1. 出队后重新读取任务并校验状态，防止消费已取消/已处理的残留队列元素；
 * 2. 失败/质检不通过时删除半成品产物，避免产生孤儿文件；
 * 3. 通知使用最新任务快照，避免使用过期状态。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskDispatcher {

    private final TaskQueue taskQueue;
    private final TaskService taskService;
    private final EngineRegistry engineRegistry;
    private final QcChecker qcChecker;
    private final FileService fileService;
    private final NotificationService notificationService;
    private final StorageService storageService;

    /**
     * 从队列取出一个任务并异步执行（由 TaskPollScheduler 或管理端手动触发）
     */
    @Async("conversionExecutor")
    public void dispatch() {
        ConvertTask dequeued;
        try {
            dequeued = taskQueue.dequeue();
        } catch (Exception e) {
            log.error("出队失败", e);
            return;
        }
        if (dequeued == null) {
            return;
        }
        try {
            ConvertTask fresh = taskService.getEntity(dequeued.getId());
            if (!TaskStatusEnum.QUEUED.name().equals(fresh.getStatus())) {
                log.warn("跳过非排队状态任务: taskId={}, status={}", fresh.getId(), fresh.getStatus());
                return;
            }
            execute(fresh);
        } catch (BizException e) {
            log.warn("任务不存在或无法执行: taskId={}, reason={}", dequeued.getId(), e.getMessage());
        }
    }

    /**
     * 执行单个任务的完整闭环（同步版本，便于单元测试与手动重放）
     */
    public void execute(ConvertTask task) {
        log.info("开始执行转换任务: taskId={}, targetFormat={}", task.getId(), task.getTargetFormat());
        String outputPath = null;
        try {
            taskService.markConverting(task.getId());
            ConvertTask converted = engineRegistry.execute(task);
            outputPath = converted.getOutputPath();

            taskService.markQc(task.getId());
            boolean passed = qcChecker.check(converted);
            if (!passed) {
                cleanup(outputPath);
                fail(task.getId(), "质量检查未通过，请检查源文件或调整参数后重试");
                return;
            }

            FileRecord result = fileService.registerResult(converted, outputPath);
            taskService.markSuccess(task.getId(), result.getId());
            notifySuccess(task.getId());
            log.info("任务转换成功: taskId={}, resultFileId={}", task.getId(), result.getId());
        } catch (BizException e) {
            log.warn("任务执行失败(业务异常): taskId={}, reason={}", task.getId(), e.getMessage());
            cleanup(outputPath);
            fail(task.getId(), e.getMessage());
        } catch (Exception e) {
            log.error("任务执行异常: taskId={}", task.getId(), e);
            cleanup(outputPath);
            fail(task.getId(), "转换引擎异常，请稍后重试");
        }
    }

    private void fail(Long taskId, String reason) {
        try {
            taskService.failTask(taskId, reason);
        } catch (Exception e) {
            log.error("标记任务失败时出错: taskId={}", taskId, e);
        }
        try {
            ConvertTask latest = taskService.getEntity(taskId);
            notificationService.notifyTaskFailed(latest, reason);
        } catch (Exception e) {
            log.warn("发送失败通知时出错: taskId={}", taskId, e);
        }
    }

    private void notifySuccess(Long taskId) {
        try {
            ConvertTask latest = taskService.getEntity(taskId);
            notificationService.notifyTaskSuccess(latest);
        } catch (Exception e) {
            log.warn("发送成功通知时出错: taskId={}", taskId, e);
        }
    }

    private void cleanup(String outputPath) {
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        try {
            storageService.delete(outputPath);
        } catch (Exception e) {
            log.warn("清理半成品产物失败: {}", outputPath, e);
        }
    }
}
