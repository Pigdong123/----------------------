package com.campus.convert.engine.qc;

import com.campus.convert.domain.entity.ConvertTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * AI 质检器（预留接入点）
 * <p>
 * 审计修复：原实现在 provider=ai 时直接抛 UnsupportedOperationException，导致整个转换链路不可用。
 * 现改为"AI 不可用时自动降级到规则校验"，保证主流程始终可用。
 * <p>
 * 后续接入方向：
 * - 音频：检测削波/失真（峰值、SNR）
 * - PDF：比对源文档页数与输出页数
 * - 图片：拉普拉斯方差检测模糊
 */
@Component
@ConditionalOnProperty(name = "campus.engine.qc.provider", havingValue = "ai")
public class AiQcChecker implements QcChecker {

    @Value("${campus.engine.qc.ai-enabled:false}")
    private boolean aiEnabled;

    @Override
    public boolean check(ConvertTask task) {
        // AI 质检尚未接入，始终走规则降级，避免阻断转换闭环
        return ruleFallback(task);
    }

    @Override
    public String provider() {
        return aiEnabled ? "ai(fallback:rule)" : "ai";
    }

    private boolean ruleFallback(ConvertTask task) {
        String outputPath = task == null ? null : task.getOutputPath();
        if (outputPath == null || outputPath.isBlank()) {
            return false;
        }
        Path path = Paths.get(outputPath);
        try {
            return Files.isRegularFile(path) && Files.size(path) > 0;
        } catch (IOException e) {
            return false;
        }
    }
}
