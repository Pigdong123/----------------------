package com.campus.convert.engine.impl;

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
 * CAJ 转换引擎（外部工具 caj2pdf）：caj → pdf
 * <p>
 * 可选引擎：未安装 caj2pdf 时不影响系统整体健康。
 */
@Component
@Slf4j
public class CajConversionEngine implements ConversionEngine {

    private static final Set<String> SOURCE_EXTS = Set.of("caj");
    private static final Set<String> TARGET_EXTS = Set.of("pdf");

    @Value("${campus.engine.caj2pdf-path:caj2pdf}")
    private String caj2pdfPath;

    @Value("${campus.engine.task-timeout-minutes:30}")
    private int taskTimeoutMinutes;

    @Override
    public EngineType getType() {
        return EngineType.CAJ;
    }

    @Override
    public boolean supports(String sourceExt, String targetExt) {
        return SOURCE_EXTS.contains(sourceExt.toLowerCase(Locale.ROOT))
                && TARGET_EXTS.contains(targetExt.toLowerCase(Locale.ROOT));
    }

    @Override
    public ConvertTask convert(ConvertTask task, String sourcePath, String targetPath) {
        EngineSupport.ensureParent(targetPath);
        List<String> command = List.of(caj2pdfPath, "convert", sourcePath, "-o", targetPath);
        ProcessRunner.run(command, taskTimeoutMinutes, "caj2pdf");
        EngineSupport.ensureOutput(targetPath, "caj2pdf");
        log.info("CAJ 转换完成: taskId={}", task.getId());
        return task;
    }

    @Override
    public boolean healthCheck() {
        try {
            ProcessRunner.runAndCapture(List.of(caj2pdfPath, "--help"), 1, "caj2pdf");
            return true;
        } catch (Exception e) {
            log.warn("caj2pdf 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean optional() {
        return true;
    }
}
