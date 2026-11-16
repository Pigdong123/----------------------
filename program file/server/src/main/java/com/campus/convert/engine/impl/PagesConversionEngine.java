package com.campus.convert.engine.impl;

import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.engine.ConversionEngine;
import com.campus.convert.engine.EngineSupport;
import com.campus.convert.engine.EngineType;
import com.campus.convert.engine.ProcessRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Apple Pages 转换引擎（外部转换器）：pages → pdf
 * <p>
 * Pages 为闭源格式，LibreOffice 无法直接解析。本引擎通过
 * {@code campus.engine.pages-command} 接入外部转换器（macOS Pages 导出脚本、
 * Aspose.Words、pages2pdf 等），未配置时给出可读提示。
 * <p>
 * 可选引擎：未配置/未安装时不影响系统整体健康。
 */
@Component
@Slf4j
public class PagesConversionEngine implements ConversionEngine {

    private static final Set<String> SOURCE_EXTS = Set.of("pages");
    private static final Set<String> TARGET_EXTS = Set.of("pdf");

    /**
     * 外部转换器命令，约定调用方式：{@code <command> <source> <target>}
     */
    @Value("${campus.engine.pages-command:}")
    private String pagesCommand;

    @Value("${campus.engine.task-timeout-minutes:30}")
    private int taskTimeoutMinutes;

    @Override
    public EngineType getType() {
        return EngineType.PAGES;
    }

    @Override
    public boolean supports(String sourceExt, String targetExt) {
        return SOURCE_EXTS.contains(sourceExt.toLowerCase(Locale.ROOT))
                && TARGET_EXTS.contains(targetExt.toLowerCase(Locale.ROOT));
    }

    @Override
    public ConvertTask convert(ConvertTask task, String sourcePath, String targetPath) {
        if (pagesCommand == null || pagesCommand.isBlank()) {
            throw new BizException(ResultCode.FORMAT_NOT_SUPPORTED,
                    "Pages 转换器未配置：请在 macOS 的 Pages 中导出 PDF，"
                            + "或配置 campus.engine.pages-command 接入转换器");
        }
        EngineSupport.ensureParent(targetPath);
        ProcessRunner.run(List.of(pagesCommand, sourcePath, targetPath),
                taskTimeoutMinutes, "pages-converter");
        EngineSupport.ensureOutput(targetPath, "pages-converter");
        log.info("Pages 转换完成: taskId={}", task.getId());
        return task;
    }

    @Override
    public boolean healthCheck() {
        if (pagesCommand == null || pagesCommand.isBlank()) {
            return false;
        }
        try {
            ProcessRunner.runAndCapture(List.of(pagesCommand, "--version"), 1, "pages-converter");
            return true;
        } catch (Exception e) {
            log.warn("Pages 转换器健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean optional() {
        return true;
    }
}
