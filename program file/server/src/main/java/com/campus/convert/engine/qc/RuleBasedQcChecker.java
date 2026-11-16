package com.campus.convert.engine.qc;

import com.campus.convert.domain.entity.ConvertTask;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 基于规则的质检器（默认，可离线运行）
 * <p>
 * 审计修复：原实现无条件返回 true，等于没有质检。现校验：
 * - 输出文件存在且大小 > 0；
 * - 依据目标格式校验文件魔数（PDF / WebP / MP3），识别空文件与格式错写。
 */
@Component
@ConditionalOnProperty(name = "campus.engine.qc.provider", havingValue = "rule", matchIfMissing = true)
public class RuleBasedQcChecker implements QcChecker {

    private static final int HEADER_SIZE = 16;

    @Override
    public boolean check(ConvertTask task) {
        String outputPath = task == null ? null : task.getOutputPath();
        if (outputPath == null || outputPath.isBlank()) {
            return false;
        }
        Path path = Paths.get(outputPath);
        try {
            if (!Files.isRegularFile(path) || Files.size(path) <= 0) {
                return false;
            }
            String ext = task.getTargetFormat() == null ? "" : task.getTargetFormat().toLowerCase();
            byte[] head = readHeader(path);
            return switch (ext) {
                case "pdf" -> startsWith(head, new byte[]{'%', 'P', 'D', 'F'});
                case "webp" -> isWebp(head);
                case "mp3" -> isMp3(head);
                default -> true;
            };
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String provider() {
        return "rule";
    }

    private byte[] readHeader(Path path) throws IOException {
        byte[] header = new byte[HEADER_SIZE];
        try (InputStream in = Files.newInputStream(path)) {
            int read = in.read(header);
            if (read <= 0) {
                return new byte[0];
            }
            if (read < HEADER_SIZE) {
                byte[] trimmed = new byte[read];
                System.arraycopy(header, 0, trimmed, 0, read);
                return trimmed;
            }
        }
        return header;
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isWebp(byte[] head) {
        return head.length >= 12
                && startsWith(head, new byte[]{'R', 'I', 'F', 'F'})
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
    }

    private boolean isMp3(byte[] head) {
        if (head.length >= 3 && head[0] == 'I' && head[1] == 'D' && head[2] == '3') {
            return true;
        }
        // MPEG-1 Layer III 帧同步：0xFFEx
        return head.length >= 2 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0;
    }
}
