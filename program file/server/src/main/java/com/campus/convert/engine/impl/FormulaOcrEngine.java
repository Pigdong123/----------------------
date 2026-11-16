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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 公式 / OCR 引擎：
 * - 图片 → latex：调用 pix2tex（LaTeX-OCR）
 * - 图片 → txt/md：调用 Tesseract OCR
 * <p>
 * 可选引擎：未安装外部工具时不影响系统整体健康。
 */
@Component
@Slf4j
public class FormulaOcrEngine implements ConversionEngine {

    private static final Set<String> SOURCE_EXTS = Set.of(
            "png", "jpg", "jpeg", "heic", "heif", "bmp", "tiff");
    private static final Set<String> TARGET_EXTS = Set.of("txt", "md", "latex", "tex");

    @Value("${campus.engine.tesseract-path:tesseract}")
    private String tesseractPath;

    @Value("${campus.engine.tesseract-lang:chi_sim+eng}")
    private String tesseractLang;

    @Value("${campus.engine.pix2tex-path:pix2tex}")
    private String pix2texPath;

    @Value("${campus.engine.task-timeout-minutes:30}")
    private int taskTimeoutMinutes;

    @Override
    public EngineType getType() {
        return EngineType.FORMULA_OCR;
    }

    @Override
    public boolean supports(String sourceExt, String targetExt) {
        return SOURCE_EXTS.contains(sourceExt.toLowerCase(Locale.ROOT))
                && TARGET_EXTS.contains(targetExt.toLowerCase(Locale.ROOT));
    }

    @Override
    public ConvertTask convert(ConvertTask task, String sourcePath, String targetPath) {
        String target = task.getTargetFormat().toLowerCase(Locale.ROOT);
        String output = isFormulaTarget(target)
                ? runPix2tex(sourcePath)
                : runTesseract(sourcePath);

        if (output == null || output.isBlank()) {
            throw new BizException(ResultCode.TASK_CONVERT_FAILED, "OCR 未识别到有效内容");
        }
        try {
            EngineSupport.ensureParent(targetPath);
            Files.writeString(Path.of(targetPath), output, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BizException(ResultCode.TASK_CONVERT_FAILED, "OCR 结果写入失败: " + e.getMessage());
        }
        EngineSupport.ensureOutput(targetPath, "OCR");
        log.info("OCR 转换完成: taskId={}, target={}", task.getId(), target);
        return task;
    }

    private boolean isFormulaTarget(String target) {
        return "latex".equals(target) || "tex".equals(target);
    }

    private String runPix2tex(String sourcePath) {
        try {
            return ProcessRunner.runAndCapture(List.of(pix2texPath, sourcePath),
                    taskTimeoutMinutes, "pix2tex");
        } catch (Exception e) {
            throw new BizException(ResultCode.TASK_CONVERT_FAILED,
                    "公式识别失败（请确认已安装 pix2tex）: " + e.getMessage());
        }
    }

    private String runTesseract(String sourcePath) {
        try {
            return ProcessRunner.runAndCapture(
                    List.of(tesseractPath, sourcePath, "stdout", "-l", tesseractLang),
                    taskTimeoutMinutes, "tesseract");
        } catch (Exception e) {
            throw new BizException(ResultCode.TASK_CONVERT_FAILED,
                    "OCR 识别失败（请确认已安装 Tesseract）: " + e.getMessage());
        }
    }

    @Override
    public boolean healthCheck() {
        return probe(tesseractPath, "--version") || probe(pix2texPath, "--version");
    }

    private boolean probe(String command, String arg) {
        try {
            ProcessRunner.runAndCapture(List.of(command, arg), 1, command);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean optional() {
        return true;
    }
}
