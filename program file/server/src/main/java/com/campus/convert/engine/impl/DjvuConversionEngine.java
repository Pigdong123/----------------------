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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * DjVu 转换引擎（外部工具 ddjvu）：djvu → pdf
 * <p>
 * 可选引擎：未安装 DjVuLibre 时不影响系统整体健康。
 */
@Component
@Slf4j
public class DjvuConversionEngine implements ConversionEngine {

    private static final Set<String> SOURCE_EXTS = Set.of("djvu", "djv");
    private static final Set<String> TARGET_EXTS = Set.of("pdf");

    @Value("${campus.engine.ddjvu-path:ddjvu}")
    private String ddjvuPath;

    @Value("${campus.engine.task-timeout-minutes:30}")
    private int taskTimeoutMinutes;

    @Override
    public EngineType getType() {
        return EngineType.DJVU;
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
        EngineSupport.ensureParent(targetPath);

        List<String> command = new ArrayList<>();
        command.add(ddjvuPath);
        command.add("-format=pdf");
        command.add("-quality=" + quality);
        command.add(sourcePath);
        command.add(targetPath);
        ProcessRunner.run(command, taskTimeoutMinutes, "ddjvu");
        EngineSupport.ensureOutput(targetPath, "ddjvu");
        log.info("DjVu 转换完成: taskId={}", task.getId());
        return task;
    }

    @Override
    public boolean healthCheck() {
        try {
            ProcessRunner.runAndCapture(List.of(ddjvuPath, "--help"), 1, "ddjvu");
            return true;
        } catch (Exception e) {
            log.warn("ddjvu 健康检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public boolean optional() {
        return true;
    }
}
