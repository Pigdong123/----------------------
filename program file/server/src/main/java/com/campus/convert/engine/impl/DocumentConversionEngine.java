package com.campus.convert.engine.impl;

import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.engine.ConversionEngine;
import com.campus.convert.engine.EngineSupport;
import com.campus.convert.engine.EngineType;
import com.campus.convert.engine.JsonParams;
import com.campus.convert.engine.ProcessRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 文档转换引擎：LibreOffice（默认）/ Pandoc（备选）
 * <p>
 * 支持：doc/docx/ppt/pptx → pdf
 * <p>
 * 审计修复：
 * 1. 实现真实转换（原为 UnsupportedOperationException）；
 * 2. 处理 soffice 实际输出文件名与目标路径不一致的问题（移动/改名）；
 * 3. 为每个任务使用独立 UserInstallation，避免并发 soffice 抢占同一用户配置目录。
 */
@Component
@Slf4j
public class DocumentConversionEngine implements ConversionEngine {

    private static final Set<String> SOURCE_EXTS = Set.of("doc", "docx", "ppt", "pptx");
    private static final Set<String> TARGET_EXTS = Set.of("pdf");

    @Value("${campus.engine.libreoffice-path:soffice}")
    private String libreofficePath;

    @Value("${campus.engine.task-timeout-minutes:30}")
    private int taskTimeoutMinutes;

    @Override
    public EngineType getType() {
        return EngineType.LIBREOFFICE;
    }

    @Override
    public boolean supports(String sourceExt, String targetExt) {
        return SOURCE_EXTS.contains(sourceExt.toLowerCase(Locale.ROOT))
                && TARGET_EXTS.contains(targetExt.toLowerCase(Locale.ROOT));
    }

    @Override
    public ConvertTask convert(ConvertTask task, String sourcePath, String targetPath) {
        String clarity = JsonParams.string(task.getParams(), "clarity", "standard");
        EngineSupport.ensureParent(targetPath);

        Path target = Paths.get(targetPath).toAbsolutePath().normalize();
        Path outDir = target.getParent();
        if (outDir == null) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "产物目录非法");
        }

        String profileDir = Paths.get(System.getProperty("java.io.tmpdir"),
                "lo-profile-" + task.getId()).toUri().toString();

        log.info("文档转换开始: taskId={}, clarity={}, outDir={}", task.getId(), clarity, outDir);

        List<String> command = new ArrayList<>();
        command.add(libreofficePath);
        command.add("--headless");
        command.add("--norestore");
        command.add("--nolockcheck");
        command.add("-env:UserInstallation=" + profileDir);
        command.add("--convert-to");
        command.add("pdf");
        command.add("--outdir");
        command.add(outDir.toString());
        command.add(sourcePath);

        ProcessRunner.run(command, taskTimeoutMinutes, "LibreOffice");

        Path produced = outDir.resolve(EngineSupport.baseName(sourcePath) + ".pdf");
        try {
            if (!produced.toAbsolutePath().normalize().equals(target)) {
                Files.move(produced, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BizException(ResultCode.TASK_CONVERT_FAILED,
                    "LibreOffice 产物重命名失败: " + e.getMessage());
        }

        EngineSupport.ensureOutput(targetPath, "LibreOffice");
        return task;
    }

    @Override
    public boolean healthCheck() {
        try {
            Process process = new ProcessBuilder(libreofficePath, "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            log.warn("LibreOffice 健康检查失败", e);
            return false;
        }
    }
}
