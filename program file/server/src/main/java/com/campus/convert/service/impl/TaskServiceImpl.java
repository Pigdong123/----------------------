package com.campus.convert.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.convert.ai.AiService;
import com.campus.convert.common.BaseContext;
import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.dto.TaskCreateRequest;
import com.campus.convert.domain.dto.TaskResponse;
import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.domain.entity.FileRecord;
import com.campus.convert.domain.entity.FormatTemplate;
import com.campus.convert.domain.enums.TaskStatusEnum;
import com.campus.convert.mapper.ConvertTaskMapper;
import com.campus.convert.mapper.FileRecordMapper;
import com.campus.convert.queue.TaskQueue;
import com.campus.convert.security.JwtUtil;
import com.campus.convert.service.FileService;
import com.campus.convert.service.FormatTemplateService;
import com.campus.convert.service.QuotaService;
import com.campus.convert.service.TaskService;
import com.campus.convert.storage.StorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 转换任务服务实现（核心状态机）
 * <p>
 * 审计修复：
 * 1. 所有状态流转改为 CAS（WHERE status = expected），避免并发重复执行；
 * 2. 查询/取消/重试/下载统一做归属校验，修复 IDOR；
 * 3. 创建任务前先校验源文件，失败不再白白消耗配额；
 * 4. 重试增加次数上限，并清理上一次的错误/产物字段。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskServiceImpl implements TaskService {

    private final ConvertTaskMapper convertTaskMapper;
    private final FileRecordMapper fileRecordMapper;
    private final FormatTemplateService formatTemplateService;
    private final QuotaService quotaService;
    private final FileService fileService;
    private final TaskQueue taskQueue;
    private final StorageService storageService;
    private final JwtUtil jwtUtil;
    private final AiService aiService;
    private final ObjectMapper objectMapper;

    @Value("${campus.file.expire-days:7}")
    private int expireDays;

    @Value("${campus.task.max-retry:3}")
    private int maxRetry;

    @Override
    public TaskResponse create(TaskCreateRequest request) {
        Long userId = BaseContext.getUserId();
        FileRecord source = fileService.getOwned(request.getSourceFileId());

        if (source.getExpireAt() != null && source.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.FILE_EXPIRED, "源文件已过期，请重新上传");
        }
        if (!storageService.exists(source.getStoragePath())
                || source.getSize() == null || source.getSize() <= 0) {
            throw new BizException(ResultCode.FILE_CORRUPTED, "源文件校验失败：文件缺失或为空");
        }

        String targetFormat = request.getTargetFormat().trim().toLowerCase(Locale.ROOT);
        FormatTemplate template = formatTemplateService.resolve(source.getExt(), targetFormat);
        if (template == null) {
            throw new BizException(ResultCode.FORMAT_NOT_SUPPORTED,
                    "暂不支持 " + source.getExt() + " → " + targetFormat);
        }

        // 校验通过后再消耗配额，避免无效任务占用额度
        quotaService.checkAndConsume(userId);

        ConvertTask task = new ConvertTask();
        task.setUserId(userId);
        task.setSourceFileId(source.getId());
        task.setTargetFormat(targetFormat);
        task.setParams(toJson(request.getParams()));
        task.setStatus(TaskStatusEnum.PENDING_VALIDATE.name());
        task.setPriority(0);
        task.setRetryCount(0);
        task.setCreatedAt(LocalDateTime.now());

        try {
            convertTaskMapper.insert(task);
            updateStatus(task.getId(), TaskStatusEnum.QUEUED);
            ConvertTask queued = getEntity(task.getId());
            taskQueue.enqueue(queued);
            return toResponse(queued);
        } catch (RuntimeException e) {
            // 入队/落库失败时回滚配额并清理半成品任务，避免"幽灵任务"占用额度
            quotaService.refundOne(userId);
            if (task.getId() != null) {
                try {
                    convertTaskMapper.deleteById(task.getId());
                } catch (Exception cleanupError) {
                    log.warn("回滚任务失败: taskId={}", task.getId(), cleanupError);
                }
            }
            throw e;
        }
    }

    @Override
    public TaskResponse get(Long taskId) {
        return toResponse(getOwnedEntity(taskId));
    }

    @Override
    public ConvertTask getEntity(Long taskId) {
        ConvertTask task = convertTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }
        return task;
    }

    /**
     * 归属校验：普通用户只能访问自己的任务；内部调度线程（BaseContext 为空）不受限。
     * <p>
     * 修复点：原 get()/cancel()/retry() 未校验任务归属，存在 IDOR 越权读取。
     */
    private ConvertTask getOwnedEntity(Long taskId) {
        ConvertTask task = getEntity(taskId);
        Long currentUserId = BaseContext.getUserId();
        if (currentUserId != null && !currentUserId.equals(task.getUserId())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权访问该任务");
        }
        return task;
    }

    @Override
    public PageResponse<TaskResponse> pageMine(long pageNum, long pageSize, String status) {
        Long userId = BaseContext.getUserId();
        long safePage = Math.max(1, pageNum);
        long safeSize = Math.min(Math.max(1, pageSize), 100);
        IPage<ConvertTask> page = convertTaskMapper.selectPage(new Page<>(safePage, safeSize),
                Wrappers.<ConvertTask>lambdaQuery()
                        .eq(ConvertTask::getUserId, userId)
                        .eq(StringUtils.hasText(status), ConvertTask::getStatus, status)
                        .orderByDesc(ConvertTask::getCreatedAt));
        List<TaskResponse> records = page.getRecords().stream().map(this::toResponse).toList();
        return PageResponse.of(page.getTotal(), page.getCurrent(), page.getSize(), records);
    }

    @Override
    public void cancel(Long taskId) {
        ConvertTask task = getOwnedEntity(taskId);
        if (!TaskStatusEnum.QUEUED.name().equals(task.getStatus())) {
            throw new BizException(ResultCode.TASK_STATUS_INVALID, "仅排队中的任务可取消");
        }
        // 先 CAS 抢占状态，再移除队列元素；即使 Redis 中仍残留，调度器也会因状态不符而跳过
        int updated = convertTaskMapper.casUpdate(taskId, TaskStatusEnum.QUEUED.name(),
                TaskStatusEnum.CANCELLED.name(), null, null, null, null, null, false, false);
        if (updated == 0) {
            throw new BizException(ResultCode.TASK_STATUS_INVALID, "任务已被调度，无法取消");
        }
        taskQueue.cancel(taskId);
    }

    @Override
    public TaskResponse retry(Long taskId) {
        ConvertTask task = getOwnedEntity(taskId);
        if (!TaskStatusEnum.FAILED.name().equals(task.getStatus())) {
            throw new BizException(ResultCode.TASK_STATUS_INVALID, "仅失败的任务可重试");
        }
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        if (retryCount >= maxRetry) {
            throw new BizException(ResultCode.FORBIDDEN,
                    "已达到最大重试次数（" + maxRetry + "），请重新上传后再试");
        }

        int toRetryable = convertTaskMapper.casUpdate(taskId, TaskStatusEnum.FAILED.name(),
                TaskStatusEnum.RETRYABLE.name(), null, null, null, null, retryCount + 1, false, false);
        if (toRetryable == 0) {
            throw new BizException(ResultCode.TASK_STATUS_INVALID, "任务状态已变化，请刷新后重试");
        }
        int toQueued = convertTaskMapper.casUpdate(taskId, TaskStatusEnum.RETRYABLE.name(),
                TaskStatusEnum.QUEUED.name(), null, null, null, null, null, true, true);
        if (toQueued == 0) {
            throw new BizException(ResultCode.TASK_STATUS_INVALID, "任务状态已变化，请刷新后重试");
        }

        ConvertTask fresh = getEntity(taskId);
        taskQueue.enqueue(fresh);
        return toResponse(fresh);
    }

    @Override
    public void updateStatus(Long taskId, TaskStatusEnum target) {
        ConvertTask task = getEntity(taskId);
        if (target.name().equals(task.getStatus())) {
            return;
        }
        TaskStatusEnum current;
        try {
            current = TaskStatusEnum.valueOf(task.getStatus());
        } catch (IllegalArgumentException e) {
            throw new BizException(ResultCode.TASK_STATUS_INVALID, "未知任务状态: " + task.getStatus());
        }
        if (!current.canTransitTo(target)) {
            throw new BizException(ResultCode.TASK_STATUS_INVALID,
                    "非法状态流转: " + current + " -> " + target);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startedAt = target == TaskStatusEnum.CONVERTING ? now : null;
        LocalDateTime finishedAt = (target == TaskStatusEnum.SUCCESS || target == TaskStatusEnum.FAILED)
                ? now : null;
        int updated = convertTaskMapper.casUpdate(taskId, current.name(), target.name(),
                startedAt, finishedAt, null, null, null, false, false);
        if (updated == 0) {
            ConvertTask latest = getEntity(taskId);
            if (target.name().equals(latest.getStatus())) {
                return; // 幂等：已被并发请求完成
            }
            throw new BizException(ResultCode.TASK_STATUS_INVALID,
                    "任务状态已被其他操作变更，请刷新后重试");
        }
    }

    @Override
    public void markConverting(Long taskId) {
        updateStatus(taskId, TaskStatusEnum.CONVERTING);
    }

    @Override
    public void markQc(Long taskId) {
        updateStatus(taskId, TaskStatusEnum.QC);
    }

    @Override
    public void markSuccess(Long taskId, Long resultFileId) {
        ConvertTask task = getEntity(taskId);
        if (TaskStatusEnum.SUCCESS.name().equals(task.getStatus())) {
            if (resultFileId != null && !resultFileId.equals(task.getResultFileId())) {
                ConvertTask patch = new ConvertTask();
                patch.setId(taskId);
                patch.setResultFileId(resultFileId);
                convertTaskMapper.updateById(patch);
            }
            return;
        }
        if (!TaskStatusEnum.QC.name().equals(task.getStatus())) {
            throw new BizException(ResultCode.TASK_STATUS_INVALID,
                    "非法状态流转: " + task.getStatus() + " -> SUCCESS");
        }
        int updated = convertTaskMapper.casUpdate(taskId, TaskStatusEnum.QC.name(),
                TaskStatusEnum.SUCCESS.name(), null, LocalDateTime.now(), resultFileId, null, null, false, false);
        if (updated == 0) {
            ConvertTask latest = getEntity(taskId);
            if (!TaskStatusEnum.SUCCESS.name().equals(latest.getStatus())) {
                throw new BizException(ResultCode.TASK_STATUS_INVALID, "任务状态已变化，无法标记成功");
            }
        }
    }

    @Override
    public void markDownloaded(Long taskId) {
        updateStatus(taskId, TaskStatusEnum.DOWNLOADED);
    }

    @Override
    public void failTask(Long taskId, String reason) {
        ConvertTask task = getEntity(taskId);
        if (TaskStatusEnum.FAILED.name().equals(task.getStatus())) {
            updateErrorMsg(taskId, reason);
            return;
        }
        TaskStatusEnum current = TaskStatusEnum.valueOf(task.getStatus());
        if (!current.canTransitTo(TaskStatusEnum.FAILED)) {
            log.warn("当前状态不允许标记失败, 忽略: taskId={}, status={}", taskId, current);
            return;
        }
        int updated = convertTaskMapper.casUpdate(taskId, current.name(), TaskStatusEnum.FAILED.name(),
                null, LocalDateTime.now(), null, reason, null, false, false);
        if (updated == 0) {
            // 并发下状态已被改变，不抛异常，避免掩盖原始失败原因
            ConvertTask latest = getEntity(taskId);
            log.warn("标记任务失败时状态已变化: taskId={}, status={}", taskId, latest.getStatus());
        }
    }

    @Override
    public FileRecord resolveResultFile(Long taskId) {
        ConvertTask task = getOwnedEntity(taskId);
        if (task.getResultFileId() == null) {
            throw new BizException(ResultCode.NOT_FOUND, "任务尚无转换产物");
        }
        FileRecord record = fileRecordMapper.selectById(task.getResultFileId());
        if (record == null) {
            throw new BizException(ResultCode.NOT_FOUND, "产物文件不存在或已被清理");
        }
        return record;
    }

    @Override
    public TaskResponse toResponse(ConvertTask task) {
        TaskResponse response = new TaskResponse();
        response.setId(task.getId());
        response.setSourceFileId(task.getSourceFileId());
        FileRecord source = fileRecordMapper.selectById(task.getSourceFileId());
        if (source != null) {
            response.setSourceFileName(source.getOriginalName());
        }
        response.setTargetFormat(task.getTargetFormat());
        response.setParams(task.getParams());
        response.setStatus(task.getStatus());
        try {
            response.setStatusDesc(TaskStatusEnum.valueOf(task.getStatus()).getDescription());
        } catch (IllegalArgumentException ignored) {
            response.setStatusDesc(task.getStatus());
        }
        response.setPriority(task.getPriority());
        response.setErrorMsg(task.getErrorMsg());
        response.setRetryCount(task.getRetryCount());
        response.setCreatedAt(task.getCreatedAt());
        response.setFinishedAt(task.getFinishedAt());
        boolean downloadable = TaskStatusEnum.SUCCESS.name().equals(task.getStatus())
                || TaskStatusEnum.DOWNLOADED.name().equals(task.getStatus());
        if (downloadable && task.getResultFileId() != null) {
            response.setDownloadToken(jwtUtil.generateDownloadToken(task.getUserId(), task.getResultFileId()));
        }
        if (StringUtils.hasText(task.getErrorMsg())) {
            response.setDiagnosis(aiService.diagnose(task.getErrorMsg()));
        }
        return response;
    }

    @Override
    public int cleanupExpired() {
        LocalDateTime before = LocalDateTime.now().minusDays(expireDays);
        List<ConvertTask> tasks = convertTaskMapper.selectExpirable(before);
        int archived = 0;
        for (ConvertTask task : tasks) {
            int updated = convertTaskMapper.casUpdate(task.getId(), task.getStatus(),
                    TaskStatusEnum.EXPIRED.name(), null, null, null, null, null, false, false);
            if (updated > 0) {
                archived++;
            }
        }
        return archived;
    }

    private void updateErrorMsg(Long taskId, String reason) {
        ConvertTask patch = new ConvertTask();
        patch.setId(taskId);
        patch.setErrorMsg(reason);
        convertTaskMapper.updateById(patch);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(ResultCode.BAD_REQUEST, "转换参数无法序列化");
        }
    }
}
