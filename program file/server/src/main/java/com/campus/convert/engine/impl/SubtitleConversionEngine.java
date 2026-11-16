package com.campus.convert.engine.impl;

import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.engine.ConversionEngine;
import com.campus.convert.engine.EngineSupport;
import com.campus.convert.engine.EngineType;
import com.campus.convert.engine.subtitle.SubtitleConverter;
import com.campus.convert.engine.subtitle.SubtitleCue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 字幕转换引擎：SRT / VTT / ASS / SSA 互转（纯 Java，无外部依赖）。
 */
@Component
@Slf4j
public class SubtitleConversionEngine implements ConversionEngine {

    private static final Set<String> SOURCE_EXTS = Set.of("srt", "vtt", "ass", "ssa");
    private static final Set<String> TARGET_EXTS = Set.of("srt", "vtt", "ass", "ssa");

    @Override
    public EngineType getType() {
        return EngineType.SUBTITLE;
    }

    @Override
    public boolean supports(String sourceExt, String targetExt) {
        return SOURCE_EXTS.contains(sourceExt.toLowerCase(Locale.ROOT))
                && TARGET_EXTS.contains(targetExt.toLowerCase(Locale.ROOT));
    }

    @Override
    public ConvertTask convert(ConvertTask task, String sourcePath, String targetPath) {
        try {
            String content = Files.readString(Path.of(sourcePath), StandardCharsets.UTF_8);
            List<SubtitleCue> cues = SubtitleConverter.parse(content, ext(sourcePath));
            if (cues.isEmpty()) {
                throw new BizException(ResultCode.TASK_CONVERT_FAILED, "未解析到有效字幕条目");
            }
            String output = SubtitleConverter.write(cues, task.getTargetFormat());
            EngineSupport.ensureParent(targetPath);
            Files.writeString(Path.of(targetPath), output, StandardCharsets.UTF_8);
            EngineSupport.ensureOutput(targetPath, "字幕引擎");
            log.info("字幕转换完成: taskId={}, cues={}", task.getId(), cues.size());
            return task;
        } catch (IOException e) {
            throw new BizException(ResultCode.TASK_CONVERT_FAILED, "字幕读写失败: " + e.getMessage());
        }
    }

    @Override
    public boolean healthCheck() {
        return true;
    }

    private String ext(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
