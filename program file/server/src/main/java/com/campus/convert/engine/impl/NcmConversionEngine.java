package com.campus.convert.engine.impl;

import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;
import com.campus.convert.domain.entity.ConvertTask;
import com.campus.convert.engine.ConversionEngine;
import com.campus.convert.engine.EngineSupport;
import com.campus.convert.engine.EngineType;
import com.campus.convert.engine.JsonParams;
import com.campus.convert.engine.ProcessRunner;
import com.campus.convert.engine.ncm.NcmDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * NCM 音频引擎：纯 Java 解密 + （必要时）ffmpeg 转码。
 * <p>
 * 支持：ncm → mp3 / flac
 */
@Component
@Slf4j
public class NcmConversionEngine implements ConversionEngine {

    private static final Set<String> SOURCE_EXTS = Set.of("ncm");
    private static final Set<String> TARGET_EXTS = Set.of("mp3", "flac");

    @Value("${campus.engine.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${campus.engine.task-timeout-minutes:30}")
    private int taskTimeoutMinutes;

    @Override
    public EngineType getType() {
        return EngineType.NCM;
    }

    @Override
    public boolean supports(String sourceExt, String targetExt) {
        return SOURCE_EXTS.contains(sourceExt.toLowerCase(Locale.ROOT))
                && TARGET_EXTS.contains(targetExt.toLowerCase(Locale.ROOT));
    }

    @Override
    public ConvertTask convert(ConvertTask task, String sourcePath, String targetPath) {
        Path tempDir = null;
        Path decrypted = null;
        try {
            tempDir = Files.createTempDirectory("ncm-");
            NcmDecoder.Result result = NcmDecoder.decrypt(Path.of(sourcePath), tempDir);
            decrypted = result.file();

            EngineSupport.ensureParent(targetPath);
            String target = task.getTargetFormat().toLowerCase(Locale.ROOT);
            if (result.ext().equals(target)) {
                Files.copy(decrypted, Path.of(targetPath), StandardCopyOption.REPLACE_EXISTING);
            } else {
                String bitrate = JsonParams.string(task.getParams(), "bitrate", "320k");
                if (!bitrate.matches("\\d{2,3}k")) {
                    bitrate = "320k";
                }
                List<String> command = List.of(
                        ffmpegPath, "-y", "-nostdin", "-loglevel", "error",
                        "-i", decrypted.toString(), "-vn", "-b:a", bitrate, targetPath);
                ProcessRunner.run(command, taskTimeoutMinutes, "ffmpeg");
            }
            EngineSupport.ensureOutput(targetPath, "NCM");
            log.info("NCM 解密转换完成: taskId={}, sourceExt={}", task.getId(), result.ext());
            return task;
        } catch (IOException e) {
            throw new BizException(ResultCode.TASK_CONVERT_FAILED, "NCM 解密失败: " + e.getMessage());
        } finally {
            if (decrypted != null) {
                try {
                    Files.deleteIfExists(decrypted);
                } catch (IOException ignored) {
                    // 临时文件清理失败不影响结果
                }
            }
            if (tempDir != null) {
                try {
                    Files.deleteIfExists(tempDir);
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }
    }

    @Override
    public boolean healthCheck() {
        return true;
    }
}
