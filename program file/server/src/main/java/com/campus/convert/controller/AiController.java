package com.campus.convert.controller;

import com.campus.convert.ai.AiService;
import com.campus.convert.common.Result;
import com.campus.convert.domain.dto.RecommendResponse;
import com.campus.convert.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "AI 增强（可选、可降级）")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final FileService fileService;

    @Operation(summary = "AI 服务状态")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", aiService.enabled());
        map.put("provider", aiService.provider());
        return Result.ok(map);
    }

    @Operation(summary = "智能格式推荐")
    @GetMapping("/recommend/{fileId}")
    public Result<RecommendResponse> recommend(@PathVariable Long fileId) {
        fileService.getOwned(fileId);
        return Result.ok(aiService.recommend(fileId));
    }

    @Operation(summary = "参数推荐")
    @GetMapping("/params")
    public Result<Map<String, Object>> params(@RequestParam String sourceExt,
                                              @RequestParam String targetFormat,
                                              @RequestParam(defaultValue = "0") long sizeBytes) {
        return Result.ok(aiService.recommendParams(sourceExt, targetFormat, sizeBytes));
    }

    @Operation(summary = "异常诊断：把失败日志翻译为用户语言")
    @GetMapping("/diagnose")
    public Result<String> diagnose(@RequestParam String message) {
        return Result.ok(aiService.diagnose(message));
    }

    @Operation(summary = "OCR 与摘要")
    @PostMapping("/ocr/{fileId}")
    public Result<String> ocr(@PathVariable Long fileId) {
        fileService.getOwned(fileId);
        return Result.ok(aiService.ocrAndSummarize(fileId));
    }

    @Operation(summary = "智能客服")
    @GetMapping("/chat")
    public Result<String> chat(@RequestParam String question) {
        return Result.ok(aiService.chat(question));
    }
}
