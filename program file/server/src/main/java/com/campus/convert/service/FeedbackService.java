package com.campus.convert.service;

import com.campus.convert.domain.dto.FeedbackQuery;
import com.campus.convert.domain.dto.FeedbackReplyRequest;
import com.campus.convert.domain.dto.FeedbackRequest;
import com.campus.convert.domain.dto.FeedbackResponse;
import com.campus.convert.domain.dto.FeedbackStatsResponse;
import com.campus.convert.domain.dto.FeedbackStatusRequest;
import com.campus.convert.domain.dto.PageResponse;

/**
 * 反馈服务：任务反馈 + 通用反馈 + 管理端处理
 */
public interface FeedbackService {

    FeedbackResponse submit(FeedbackRequest request);

    PageResponse<FeedbackResponse> pageMine(FeedbackQuery query);

    FeedbackResponse getMine(Long id);

    void withdraw(Long id);

    PageResponse<FeedbackResponse> pageByTask(Long taskId, long pageNum, long pageSize);

    PageResponse<FeedbackResponse> pageAdmin(FeedbackQuery query);

    FeedbackResponse getAdmin(Long id);

    FeedbackResponse reply(Long id, FeedbackReplyRequest request);

    FeedbackResponse updateStatus(Long id, FeedbackStatusRequest request);

    FeedbackStatsResponse stats();
}
