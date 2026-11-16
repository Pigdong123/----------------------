package com.campus.convert.ai.impl;

import com.campus.convert.ai.AiService;
import com.campus.convert.domain.dto.RecommendResponse;
import com.campus.convert.domain.entity.FileRecord;
import com.campus.convert.domain.entity.FormatTemplate;
import com.campus.convert.mapper.FileRecordMapper;
import com.campus.convert.service.FormatTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 大模型 AI 实现（OpenAI 兼容接口，provider=llm 时启用）
 * <p>
 * 审计修复：
 * 1. 实现最小可用的 /chat/completions 调用；
 * 2. 任何异常/未配置时自动降级为规则结果，绝不阻断主流程；
 * 3. 补全 recommend / recommendParams / diagnose / chat 的降级逻辑。
 * <p>
 * 配置示例：
 * campus.ai.provider=llm
 * campus.ai.llm.base-url=https://api.openai.com/v1
 * campus.ai.llm.api-key=sk-xxx
 * campus.ai.llm.model=gpt-4o-mini
 */
@Service
@ConditionalOnProperty(name = "campus.ai.provider", havingValue = "llm")
@RequiredArgsConstructor
@Slf4j
public class LlmAiServiceImpl implements AiService {

    private static final Map<String, List<String>> FALLBACK = Map.of(
            "docx", List.of("pdf"), "doc", List.of("pdf"),
            "pptx", List.of("pdf"), "ppt", List.of("pdf"),
            "flac", List.of("mp3"), "wav", List.of("mp3"), "m4a", List.of("mp3"),
            "png", List.of("webp"), "jpg", List.of("webp"), "jpeg", List.of("webp"));

    private final FormatTemplateService formatTemplateService;
    private final FileRecordMapper fileRecordMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${campus.ai.enabled:true}")
    private boolean enabled;

    @Value("${campus.ai.llm.base-url:}")
    private String baseUrl;

    @Value("${campus.ai.llm.api-key:}")
    private String apiKey;

    @Value("${campus.ai.llm.model:}")
    private String model;

    @Override
    public boolean enabled() {
        return enabled;
    }

    @Override
    public String provider() {
        return "llm";
    }

    @Override
    public List<String> recommendFormats(String sourceExt) {
        String ext = normalize(sourceExt);
        List<String> targets = formatTemplateService.listBySourceExt(ext).stream()
                .map(FormatTemplate::getTargetExt)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        return targets.isEmpty() ? FALLBACK.getOrDefault(ext, List.of()) : targets;
    }

    @Override
    public RecommendResponse recommend(Long fileId) {
        RecommendResponse response = new RecommendResponse();
        response.setFileId(fileId);
        response.setProvider(provider());
        FileRecord file = fileRecordMapper.selectById(fileId);
        if (file == null) {
            response.setRecommendedFormats(List.of());
            response.setReason("文件不存在");
            return response;
        }
        response.setSourceExt(file.getExt());
        response.setRecommendedFormats(recommendFormats(file.getExt()));
        response.setReason("根据格式模板推荐（LLM 不可用时自动降级）");
        return response;
    }

    @Override
    public Map<String, Object> recommendParams(String sourceExt, String targetFormat, long sizeBytes) {
        Map<String, Object> params = new LinkedHashMap<>();
        switch (normalize(targetFormat)) {
            case "mp3" -> params.put("bitrate", sizeBytes > 20L * 1024 * 1024 ? "128k" : "192k");
            case "webp" -> params.put("quality", 80);
            case "pdf" -> params.put("clarity", "standard");
            default -> params.put("preset", "default");
        }
        return params;
    }

    @Override
    public String diagnose(String errorMsg) {
        String ai = chatCompletion("请用一句中文向学生解释这个格式转换失败原因，并给出处理建议："
                + (errorMsg == null ? "" : errorMsg));
        return ai != null ? ai : ruleDiagnose(errorMsg);
    }

    @Override
    public String ocrAndSummarize(Long fileId) {
        return "OCR 与摘要功能尚未接入（当前为 LLM 降级模式）。";
    }

    @Override
    public String chat(String question) {
        String ai = chatCompletion("你是校园课程资源格式转换系统的客服，请用简洁中文回答：" + question);
        return ai != null ? ai : ruleChat(question);
    }

    /**
     * 调用 OpenAI 兼容的 /chat/completions；任何异常返回 null 以触发降级。
     */
    private String chatCompletion(String prompt) {
        if (!enabled || isBlank(baseUrl) || isBlank(apiKey) || isBlank(model)) {
            return null;
        }
        try {
            String endpoint = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
            Map<String, Object> payload = Map.of(
                    "model", model,
                    "temperature", 0.2,
                    "messages", List.of(Map.of("role", "user", "content", prompt)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("LLM 调用失败: status={}", response.statusCode());
                return null;
            }
            JsonNode content = objectMapper.readTree(response.body()).at("/choices/0/message/content");
            return content.isMissingNode() || content.isNull() ? null : content.asText();
        } catch (Exception e) {
            log.warn("LLM 调用异常，降级为规则回答: {}", e.getMessage());
            return null;
        }
    }

    private String ruleDiagnose(String errorMsg) {
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

    private String ruleChat(String question) {
        String q = question == null ? "" : question;
        if (q.contains("失败") || q.contains("为什么")) {
            return "转换失败常见原因：格式不支持、文件损坏、参数过低或存储不足，可在任务详情查看诊断信息。";
        }
        if (q.contains("格式") || q.contains("怎么选")) {
            return "课件建议转 PDF，听力建议转 MP3，图片建议转 WebP；上传后会给出推荐格式。";
        }
        return "我可以解答格式选择、转换失败原因等问题。";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String ext) {
        return ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT).replace(".", "");
    }
}
