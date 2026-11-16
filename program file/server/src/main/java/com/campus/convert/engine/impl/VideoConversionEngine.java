package com.campus.convert.engine.impl;

import com.campus.convert.common.BizException;
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
 * 视频转换引擎：ffmpeg
 * <p>
 * 支持：m4s / mov / hevc / mkv / avi → mp4
 * <p>
 * 策略：
 * 1. 优先 `-c copy` 无损封装（速度快、无画质损失）；
 * 2. 封装失败时自动回退到 H.264/AAC 转码（兼容性最好）；
 * 3. HEVC 裸流使用 `-f hevc` 指定输入格式。
 */
@Component
@Slf4j
public class VideoConversionEngine implements ConversionEngine {

    private static final Set<String> SOURCE_EXTS = Set.of("m4s", "mov", "hevc", "mkv", "avi");
    private static final Set<String> TARGET_EXTS = Set.of("mp4");
    private static final Set<String> ALLOWED_VCODECS = Set.of("libx264", "libx265");
    private static final Set<String> ALLOWED_PRESETS = Set.of(
            "ultrafast", "superfast", "veryfast", "faster", "fast",
            "medium", "slow", "slower", "veryslow");

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
        EngineSupport.ensureParent(targetPath);
        String mode = JsonParams.string(task.getParams(), "mode", "auto");
        String sourceExt = ext(sourcePath);

        if (!"transcode".equalsIgnoreCase(mode)) {
            try {
                runRemux(sourceExt, sourcePath, targetPath);
                EngineSupport.ensureOutput(targetPath, "ffmpeg(remux)");
                log.info("视频无损封装完成: taskId={}", task.getId());
                return task;
            } catch (BizException e) {
                log.warn("视频封装失败，回退转码: taskId={}, reason={}", task.getId(), e.getMessage());
            }
        }

        runTranscode(task, sourceExt, sourcePath, targetPath);
        EngineSupport.ensureOutput(targetPath, "ffmpeg(transcode)");
        log.info("视频转码完成: taskId={}", task.getId());
        return task;
    }

    private void runRemux(String sourceExt, String sourcePath, String targetPath) {
        List<String> command = baseCommand(sourceExt, sourcePath);
        command.add("-c");
        command.add("copy");
        command.add("-movflags");
        command.add("+faststart");
        command.add(targetPath);
        ProcessRunner.run(command, taskTimeoutMinutes, "ffmpeg");
    }

    private void runTranscode(ConvertTask task, String sourceExt, String sourcePath, String targetPath) {
        String vcodec = JsonParams.string(task.getParams(), "vcodec", "libx264");
        if (!ALLOWED_VCODECS.contains(vcodec)) {
            vcodec = "libx264";
        }
        String preset = JsonParams.string(task.getParams(), "preset", "veryfast");
        if (!ALLOWED_PRESETS.contains(preset)) {
            preset = "veryfast";
        }
        int crf = Math.max(18, Math.min(32,
                JsonParams.integer(task.getParams(), "crf", 23)));

        List<String> command = baseCommand(sourceExt, sourcePath);
        command.add("-c:v");
        command.add(vcodec);
        command.add("-preset");
        command.add(preset);
        command.add("-crf");
        command.add(String.valueOf(crf));
        command.add("-c:a");
        command.add("aac");
        command.add("-b:a");
        command.add("128k");
        command.add("-movflags");
        command.add("+faststart");
        command.add(targetPath);
        ProcessRunner.run(command, taskTimeoutMinutes, "ffmpeg");
    }

    private List<String> baseCommand(String sourceExt, String sourcePath) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        command.add("-nostdin");
        command.add("-loglevel");
        command.add("error");
        if ("hevc".equals(sourceExt)) {
            command.add("-f");
            command.add("hevc");
        }
        command.add("-i");
        command.add(sourcePath);
        return command;
    }

    private String ext(String path) {
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
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
            log.warn("ffmpeg 视频引擎健康检查失败", e);
            return false;
        }
    }
}
