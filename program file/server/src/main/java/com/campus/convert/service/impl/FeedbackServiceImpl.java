package com.campus.convert.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.convert.common.BaseContext;
import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import com.campus.convert.domain.dto.FeedbackQuery;
import com.campus.convert.domain.dto.FeedbackReplyRequest;
import com.campus.convert.domain.dto.FeedbackRequest;
import com.campus.convert.domain.dto.FeedbackResponse;
import com.campus.convert.domain.dto.FeedbackStatsResponse;
import com.campus.convert.domain.dto.FeedbackStatusRequest;
import com.campus.convert.domain.dto.PageResponse;
import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.domain.entity.Feedback;
import com.campus.convert.domain.entity.User;
import com.campus.convert.domain.enums.FeedbackCategoryEnum;
import com.campus.convert.domain.enums.FeedbackStatusEnum;
import com.campus.convert.mapper.ConvertTaskMapper;
import com.campus.convert.mapper.FeedbackMapper;
import com.campus.convert.mapper.UserMapper;
import com.campus.convert.service.AuditLogService;
import com.campus.convert.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 反馈服务实现：任务反馈 + 通用反馈 + 管理端回复/状态/统计。
 */
@Service
@RequiredArgsConstructor
public class FeedbackServiceImpl implements FeedbackService {

    private static final Set<String> PRIVILEGED_ROLES = Set.of("SYS_ADMIN", "COURSE_ADMIN", "OPS");

    private final FeedbackMapper feedbackMapper;
    private final ConvertTaskMapper convertTaskMapper;
    private final UserMapper userMapper;
    private final AuditLogService auditLogService;

    @Override
    public FeedbackResponse submit(FeedbackRequest request) {
        Long userId = requireUser();
        ConvertTask task = null;
        if (request.getTaskId() != null) {
            task = convertTaskMapper.selectById(request.getTaskId());
            if (task == null) {
                throw new BizException(ResultCode.TASK_NOT_FOUND);
            }
            if (!task.getUserId().equals(userId)) {
                throw new BizException(ResultCode.FORBIDDEN, "无权评价该任务");
            }
        }

        Feedback feedback = task == null ? null : feedbackMapper.selectOne(
                Wrappers.<Feedback>lambdaQuery().eq(Feedback::getTaskId, request.getTaskId()));
        boolean created = feedback == null;
        if (created) {
            feedback = new Feedback();
            feedback.setTaskId(request.getTaskId());
            feedback.setUserId(userId);
            feedback.setCreatedAt(LocalDateTime.now());
            feedback.setStatus(FeedbackStatusEnum.PENDING.name());
        }

        applyRequest(feedback, request);
        if (created) {
            feedbackMapper.insert(feedback);
        } else {
            // 已解决/已关闭后再次提交，重新进入待处理
            if (FeedbackStatusEnum.RESOLVED.name().equals(feedback.getStatus())
                    || FeedbackStatusEnum.CLOSED.name().equals(feedback.getStatus())) {
                feedback.setStatus(FeedbackStatusEnum.PENDING.name());
            }
            feedbackMapper.updateById(feedback);
        }
        auditLogService.record("SUBMIT_FEEDBACK", "feedback:" + feedback.getId(), feedback.getCategory());
        return toResponse(feedback, null);
    }

    @Override
    public PageResponse<FeedbackResponse> pageMine(FeedbackQuery query) {
        Long userId = requireUser();
        FeedbackQuery q = query == null ? new FeedbackQuery() : query;
        IPage<Feedback> result = feedbackMapper.selectPage(
                new Page<>(clampPage(q.getPageNum()), clampSize(q.getPageSize())),
                Wrappers.<Feedback>lambdaQuery()
                        .eq(Feedback::getUserId, userId)
                        .eq(StringUtils.hasText(q.getStatus()), Feedback::getStatus, q.getStatus())
                        .eq(StringUtils.hasText(q.getCategory()), Feedback::getCategory, q.getCategory())
                        .eq(q.getRating() != null, Feedback::getRating, q.getRating())
                        .eq(q.getTaskId() != null, Feedback::getTaskId, q.getTaskId())
                        .and(StringUtils.hasText(q.getKeyword()), w -> w
                                .like(Feedback::getTitle, q.getKeyword())
                                .or().like(Feedback::getComment, q.getKeyword()))
                        .orderByDesc(Feedback::getCreatedAt));
        List<FeedbackResponse> records = result.getRecords().stream()
                .map(f -> toResponse(f, null))
                .toList();
        return PageResponse.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public FeedbackResponse getMine(Long id) {
        Feedback feedback = getExisting(id);
        if (!feedback.getUserId().equals(requireUser())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权查看该反馈");
        }
        return toResponse(feedback, null);
    }

    @Override
    public void withdraw(Long id) {
        Feedback feedback = getExisting(id);
        if (!feedback.getUserId().equals(requireUser())) {
            throw new BizException(ResultCode.FORBIDDEN, "无权撤回该反馈");
        }
        if (!FeedbackStatusEnum.PENDING.name().equals(feedback.getStatus())) {
            throw new BizException(ResultCode.TASK_STATUS_INVALID, "仅待处理的反馈可撤回");
        }
        feedback.setStatus(FeedbackStatusEnum.CLOSED.name());
        feedbackMapper.updateById(feedback);
        auditLogService.record("WITHDRAW_FEEDBACK", "feedback:" + id, null);
    }

    @Override
    public PageResponse<FeedbackResponse> pageByTask(Long taskId, long pageNum, long pageSize) {
        ConvertTask task = convertTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(ResultCode.TASK_NOT_FOUND);
        }
        Long userId = requireUser();
        if (!isPrivileged(BaseContext.getUserRole()) && !task.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权查看该任务反馈");
        }
        IPage<Feedback> result = feedbackMapper.selectPage(
                new Page<>(clampPage(pageNum), clampSize(pageSize)),
                Wrappers.<Feedback>lambdaQuery()
                        .eq(Feedback::getTaskId, taskId)
                        .orderByDesc(Feedback::getCreatedAt));
        List<FeedbackResponse> records = result.getRecords().stream()
                .map(f -> toResponse(f, null))
                .toList();
        return PageResponse.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public PageResponse<FeedbackResponse> pageAdmin(FeedbackQuery query) {
        FeedbackQuery q = query == null ? new FeedbackQuery() : query;
        IPage<Feedback> result = feedbackMapper.selectPage(
                new Page<>(clampPage(q.getPageNum()), clampSize(q.getPageSize())),
                Wrappers.<Feedback>lambdaQuery()
                        .eq(StringUtils.hasText(q.getStatus()), Feedback::getStatus, q.getStatus())
                        .eq(StringUtils.hasText(q.getCategory()), Feedback::getCategory, q.getCategory())
                        .eq(q.getRating() != null, Feedback::getRating, q.getRating())
                        .eq(q.getTaskId() != null, Feedback::getTaskId, q.getTaskId())
                        .and(StringUtils.hasText(q.getKeyword()), w -> w
                                .like(Feedback::getTitle, q.getKeyword())
                                .or().like(Feedback::getComment, q.getKeyword()))
                        .orderByDesc(Feedback::getCreatedAt));
        Map<Long, String> usernames = usernames(result.getRecords());
        List<FeedbackResponse> records = result.getRecords().stream()
                .map(f -> toResponse(f, usernames.get(f.getUserId())))
                .toList();
        return PageResponse.of(result.getTotal(), result.getCurrent(), result.getSize(), records);
    }

    @Override
    public FeedbackResponse getAdmin(Long id) {
        Feedback feedback = getExisting(id);
        return toResponse(feedback, usernameOf(feedback.getUserId()));
    }

    @Override
    public FeedbackResponse reply(Long id, FeedbackReplyRequest request) {
        Feedback feedback = getExisting(id);
        feedback.setAdminReply(request.getReply().trim());
        feedback.setRepliedBy(requireUser());
        feedback.setRepliedAt(LocalDateTime.now());
        FeedbackStatusEnum status = StringUtils.hasText(request.getStatus())
                ? FeedbackStatusEnum.from(request.getStatus())
                : FeedbackStatusEnum.RESOLVED;
        feedback.setStatus(status.name());
        feedbackMapper.updateById(feedback);
        auditLogService.record("REPLY_FEEDBACK", "feedback:" + id, status.name());
        return toResponse(feedback, usernameOf(feedback.getUserId()));
    }

    @Override
    public FeedbackResponse updateStatus(Long id, FeedbackStatusRequest request) {
        Feedback feedback = getExisting(id);
        FeedbackStatusEnum status = FeedbackStatusEnum.from(request.getStatus());
        feedback.setStatus(status.name());
        feedbackMapper.updateById(feedback);
        auditLogService.record("UPDATE_FEEDBACK_STATUS", "feedback:" + id, status.name());
        return toResponse(feedback, usernameOf(feedback.getUserId()));
    }

    @Override
    public FeedbackStatsResponse stats() {
        FeedbackStatsResponse response = new FeedbackStatsResponse();
        Long total = feedbackMapper.selectCount(null);
        response.setTotal(total == null ? 0 : total);

        Map<String, Long> byStatus = toCountMap(feedbackMapper.countByStatus(), "status");
        response.setPending(byStatus.getOrDefault(FeedbackStatusEnum.PENDING.name(), 0L));
        response.setProcessing(byStatus.getOrDefault(FeedbackStatusEnum.PROCESSING.name(), 0L));
        response.setResolved(byStatus.getOrDefault(FeedbackStatusEnum.RESOLVED.name(), 0L));
        response.setClosed(byStatus.getOrDefault(FeedbackStatusEnum.CLOSED.name(), 0L));
        response.setByCategory(toCountMap(feedbackMapper.countByCategory(), "category"));
        response.setAvgRating(feedbackMapper.avgRating());
        return response;
    }

    private void applyRequest(Feedback feedback, FeedbackRequest request) {
        feedback.setCategory(FeedbackCategoryEnum.from(request.getCategory()).name());
        feedback.setTitle(request.getTitle());
        feedback.setRating(request.getRating());
        feedback.setComment(request.getComment().trim());
        feedback.setContact(request.getContact());
        feedback.setAnonymous(Boolean.TRUE.equals(request.getAnonymous()) ? 1 : 0);
    }

    private Feedback getExisting(Long id) {
        Feedback feedback = feedbackMapper.selectById(id);
        if (feedback == null) {
            throw new BizException(ResultCode.NOT_FOUND, "反馈不存在");
        }
        return feedback;
    }

    private Long requireUser() {
        Long userId = BaseContext.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    private boolean isPrivileged(String role) {
        return role != null && PRIVILEGED_ROLES.contains(role);
    }

    private long clampPage(long pageNum) {
        return Math.max(1, pageNum);
    }

    private long clampSize(long pageSize) {
        return Math.min(Math.max(1, pageSize), 100);
    }

    private String usernameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        return user == null ? "已注销用户" : user.getUsername();
    }

    private Map<Long, String> usernames(List<Feedback> feedbacks) {
        Set<Long> ids = feedbacks.stream()
                .map(Feedback::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    private FeedbackResponse toResponse(Feedback feedback, String username) {
        FeedbackResponse response = new FeedbackResponse();
        response.setId(feedback.getId());
        response.setTaskId(feedback.getTaskId());
        response.setUserId(feedback.getUserId());
        response.setCategory(feedback.getCategory());
        response.setTitle(feedback.getTitle());
        response.setRating(feedback.getRating());
        response.setComment(feedback.getComment());
        response.setContact(feedback.getContact());
        boolean anonymous = feedback.getAnonymous() != null && feedback.getAnonymous() == 1;
        response.setAnonymous(anonymous);
        response.setStatus(feedback.getStatus());
        try {
            response.setStatusDesc(FeedbackStatusEnum.from(feedback.getStatus()).getDescription());
        } catch (IllegalArgumentException e) {
            response.setStatusDesc(feedback.getStatus());
        }
        response.setAdminReply(feedback.getAdminReply());
        response.setRepliedBy(feedback.getRepliedBy());
        response.setRepliedAt(feedback.getRepliedAt());
        response.setCreatedAt(feedback.getCreatedAt());
        response.setUpdatedAt(feedback.getUpdatedAt() == null
                ? feedback.getCreatedAt() : feedback.getUpdatedAt());
        if (username != null) {
            response.setUsername(anonymous ? "匿名用户" : username);
        }
        return response;
    }

    private Map<String, Long> toCountMap(List<Map<String, Object>> rows, String key) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object name = getIgnoreCase(row, key);
            Object count = getIgnoreCase(row, "cnt");
            if (name != null) {
                long value = count instanceof Number number ? number.longValue() : 0L;
                result.put(String.valueOf(name).toUpperCase(Locale.ROOT), value);
            }
        }
        return result;
    }

    private Object getIgnoreCase(Map<String, Object> map, String key) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
