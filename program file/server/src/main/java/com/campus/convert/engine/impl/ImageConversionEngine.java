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
 * 图片转换引擎：ImageMagick
 * <p>
 * 支持：png/jpg/jpeg → webp
 * <p>
 * 审计修复：
 * 1. 实现真实转换（原为 UnsupportedOperationException）；
 * 2. 解析 quality 参数；
 * 3. Windows 下自动改用 `magick`，避免误调用系统自带 `convert.exe`（磁盘格式转换工具）。
 */
@Component
@Slf4j
public class ImageConversionEngine implements ConversionEngine {

    private static final Set<String> SOURCE_EXTS = Set.of("png", "jpg", "jpeg", "heic", "heif");
    private static final Set<String> TARGET_EXTS = Set.of("webp", "jpg", "png");

    @Value("${campus.engine.imagemagick-path:convert}")
    private String imagemagickPath;

    @Value("${campus.engine.task-timeout-minutes:30}")
    private int taskTimeoutMinutes;

    @Override
    public EngineType getType() {
        return EngineType.IMAGEMAGICK;
    }

    @Override
    public boolean supports(String sourceExt, String targetExt) {
        return SOURCE_EXTS.contains(sourceExt.toLowerCase(Locale.ROOT))
                && TARGET_EXTS.contains(targetExt.toLowerCase(Locale.ROOT));
    }

    @Override
    public ConvertTask convert(ConvertTask task, String sourcePath, String targetPath) {
        int quality = Math.max(1, Math.min(100,
                JsonParams.integer(task.getParams(), "quality", 80)));
        String binary = resolveBinary();
        EngineSupport.ensureParent(targetPath);

        log.info("图片转换开始: taskId={}, binary={}, quality={}", task.getId(), binary, quality);

        List<String> command = new ArrayList<>();
        command.add(binary);
        command.add(sourcePath);
        command.add("-quality");
        command.add(String.valueOf(quality));
        command.add(targetPath);

        ProcessRunner.run(command, taskTimeoutMinutes, "ImageMagick");
        EngineSupport.ensureOutput(targetPath, "ImageMagick");
        return task;
    }

    @Override
    public boolean healthCheck() {
        try {
            Process process = new ProcessBuilder(resolveBinary(), "-version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.warn("ImageMagick 健康检查失败", e);
            return false;
        }
    }

    private String resolveBinary() {
        String configured = imagemagickPath == null ? "" : imagemagickPath.trim();
        if (configured.isEmpty()) {
            configured = "convert";
        }
        // Windows 的 convert.exe 是系统磁盘转换工具，必须改用 ImageMagick 7 的 magick
        if (isWindows() && "convert".equalsIgnoreCase(configured)) {
            return "magick";
        }
        return configured;
    }

    private boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
