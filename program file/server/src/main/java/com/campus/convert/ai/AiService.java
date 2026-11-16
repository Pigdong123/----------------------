package com.campus.convert.ai;

import com.campus.convert.domain.dto.RecommendResponse;

import java.util.List;
import java.util.Map;

/**
 * AI 增强服务（对应需求 5.4，全部可选、可降级）
 * <p>
 * 实现约定：
 * - rule：本地规则引擎，离线可用，作为默认与兜底
 * - llm：大模型 API，需在 campus.ai.llm.* 配置
 * 核心转换流程不依赖本接口，任何方法异常都不应阻断主流程。
 */
public interface AiService {

    boolean enabled();

    String provider();

    /**
     * 根据源格式推荐目标格式（上传时即时推荐）
     */
    List<String> recommendFormats(String sourceExt);

    /**
     * 根据文件记录推荐目标格式并给出理由
     */
    RecommendResponse recommend(Long fileId);

    /**
     * 根据文件大小/目标格式推荐转换参数
     */
    Map<String, Object> recommendParams(String sourceExt, String targetFormat, long sizeBytes);

    /**
     * 把失败原因翻译为用户可理解的语言（异常诊断）
     */
    String diagnose(String errorMsg);

    /**
     * PDF/图片 OCR 与摘要
     */
    String ocrAndSummarize(Long fileId);

    /**
     * 智能客服问答
     */
    String chat(String question);
}
