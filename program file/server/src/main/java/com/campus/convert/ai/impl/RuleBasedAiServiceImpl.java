package com.campus.convert.ai.impl;

import com.campus.convert.ai.AiService;
import com.campus.convert.domain.dto.RecommendResponse;
import com.campus.convert.domain.entity.FileRecord;
import com.campus.convert.mapper.FileRecordMapper;
import com.campus.convert.service.FormatTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于规则的 AI 降级实现（默认，离线可用）
 */
@Service
@ConditionalOnProperty(name = "campus.ai.provider", havingValue = "rule", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class RuleBasedAiServiceImpl implements AiService {

    private final FormatTemplateService formatTemplateService;
    private final FileRecordMapper fileRecordMapper;

    @Value("${campus.ai.enabled:true}")
    private boolean enabled;

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String provider() {
        return "rule";
    }

    @Override
    public List<String> recommendFormats(String sourceExt) {
        String ext = normalize(sourceExt);
        List<String> targets = formatTemplateService.listBySourceExt(ext).stream()
                .map(t -> t.getTargetExt().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (!targets.isEmpty()) {
            return targets;
        }
        // 兜底映射（需求第 11 节第一期格式范围）
        Map<String, List<String>> fallback = Map.of(
                "docx", List.of("pdf"), "doc", List.of("pdf"),
                "pptx", List.of("pdf"), "ppt", List.of("pdf"),
                "flac", List.of("mp3"), "wav", List.of("mp3"), "m4a", List.of("mp3"),
                "png", List.of("webp"), "jpg", List.of("webp"), "jpeg", List.of("webp"));
        return fallback.getOrDefault(ext, List.of());
    }

    @Override
    public RecommendResponse recommend(Long fileId) {
        FileRecord file = fileRecordMapper.selectById(fileId);
        RecommendResponse response = new RecommendResponse();
        response.setFileId(fileId);
        response.setProvider("rule");
        if (file == null) {
            response.setRecommendedFormats(List.of());
            response.setReason("文件不存在");
            return response;
        }
        response.setSourceExt(file.getExt());
        response.setRecommendedFormats(recommendFormats(file.getExt()));
        response.setReason("根据源格式 " + file.getExt() + " 的模板匹配推荐（规则引擎）");
        return response;
    }

    @Override
    public Map<String, Object> recommendParams(String sourceExt, String targetFormat, long sizeBytes) {
        String target = normalize(targetFormat);
        Map<String, Object> params = new LinkedHashMap<>();
        switch (target) {
            case "mp3" -> params.put("bitrate", sizeBytes > 20L * 1024 * 1024 ? "128k" : "192k");
            case "webp" -> params.put("quality", 80);
            case "pdf" -> params.put("clarity", "standard");
            default -> params.put("preset", "default");
        }
        return params;
    }

    @Override
    public String diagnose(String errorMsg) {
        String msg = errorMsg == null ? "" : errorMsg;
        if (msg.contains("配额")) {
            return "今日转换次数已用完，请明日再试或联系管理员调整配额。";
        }
        if (msg.contains("格式") || msg.contains("不支持")) {
            return "该源格式与目标格式暂不在支持范围内，请更换格式或联系管理员添加模板。";
        }
        if (msg.contains("超时")) {
            return "转换耗时过长已超时，建议压缩文件体积后重试或选择更低的参数。";
        }
        if (msg.contains("存储")) {
            return "存储空间不足，请清理历史文件或联系运维扩容。";
        }
        if (msg.contains("质量")) {
            return "转换结果未通过质检，可能是源文件损坏或参数过低，请调整后重试。";
        }
        return "转换失败，请稍后重试；若持续失败请联系管理员并提供任务编号。";
    }

    @Override
    public String ocrAndSummarize(Long fileId) {
        // TODO: 接入 Tesseract / PaddleOCR + 摘要模型
        return "OCR 与摘要功能待接入（当前为规则引擎降级模式）。";
    }

    @Override
    public String chat(String question) {
        String q = question == null ? "" : question;
        if (q.contains("为什么") || q.contains("失败")) {
            return "转换失败常见原因：格式不支持、文件损坏、参数过低或存储不足，可在任务详情查看诊断信息。";
        }
        if (q.contains("格式") || q.contains("怎么选")) {
            return "课件建议转 PDF，听力建议转 MP3，图片建议转 WebP；上传后会给出推荐格式。";
        }
        return "我可以解答格式选择、转换失败原因等问题（当前为规则引擎降级模式）。";
    }

    private String normalize(String ext) {
        return ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT).replace(".", "");
    }
}
