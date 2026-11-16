package com.campus.convert.engine.impl;

import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.engine.ConversionEngine;
import com.campus.convert.engine.EngineSupport;
import com.campus.convert.engine.EngineType;
import com.campus.convert.engine.JsonParams;
import com.campus.convert.engine.ProcessRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 音频转换引擎：ffmpeg
 * <p>
 * 支持：flac/wav/m4a → mp3
 * <p>
 * 审计修复：
 * 1. 实现真实转换（原为 UnsupportedOperationException）；
 * 2. 解析 bitrate 参数并做白名单校验，非法值回退 192k；
 * 3. 统一加 -nostdin/-loglevel error，避免污染管道与阻塞。
 */
@Component
@Slf4j
public class AudioConversionEngine implements ConversionEngine {

    private static final Set<String> SOURCE_EXTS = Set.of("flac", "wav", "m4a");
    private static final Set<String> TARGET_EXTS = Set.of("mp3");
    private static final String DEFAULT_BITRATE = "192k";

    @Value("${campus.engine.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${campus.engine.task-timeout-minutes:30}")
    private int taskTimeoutMinutes;

    @Override
    public EngineType getType() {
        return EngineType.FFMPEG;
    }

    @Override
    public boolean supports(String sourceExt, String targetExt) {
        return SOURCE_EXTS.contains(sourceExt.toLowerCase(Locale.ROOT))
                && TARGET_EXTS.contains(targetExt.toLowerCase(Locale.ROOT));
    }

    @Override
    public ConvertTask convert(ConvertTask task, String sourcePath, String targetPath) {
        String bitrate = JsonParams.string(task.getParams(), "bitrate", DEFAULT_BITRATE);
        if (!bitrate.matches("\\d{2,3}k")) {
            bitrate = DEFAULT_BITRATE;
        }
        EngineSupport.ensureParent(targetPath);

        log.info("音频转换开始: taskId={}, bitrate={}", task.getId(), bitrate);

        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-nostdin");
        command.add("-loglevel");
        command.add("error");
        command.add("-i");
        command.add(sourcePath);
        command.add("-vn");
        command.add("-b:a");
        command.add(bitrate);
        command.add(targetPath);

        ProcessRunner.run(command, taskTimeoutMinutes, "ffmpeg");
        EngineSupport.ensureOutput(targetPath, "ffmpeg");
        return task;
    }

    @Override
    public boolean healthCheck() {
        try {
            Process process = new ProcessBuilder(ffmpegPath, "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.warn("ffmpeg 健康检查失败", e);
            return false;
        }
    }
}
