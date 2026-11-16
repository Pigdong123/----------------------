package com.campus.convert.engine.subtitle;

import com.campus.convert.common.BizException;
import com.campus.convert.common.ResultCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 字幕格式转换核心：SRT / VTT / ASS / SSA 互转。
 * <p>
 * 纯 Java 实现，不依赖外部二进制；解析后统一为 {@link SubtitleCue} 列表再序列化。
 */
public final class SubtitleConverter {

    private static final Pattern SRT_TIME = Pattern.compile(
            "(\\d+):(\\d+):(\\d+)[,.](\\d{1,3})");
    private static final Pattern ASS_TIME = Pattern.compile(
            "(\\d+):(\\d+):(\\d+)[.](\\d{1,2})");
    private static final Pattern ASS_TAG = Pattern.compile("\\{[^}]*}");

    private SubtitleConverter() {
    }

    public static List<SubtitleCue> parse(String content, String ext) {
        String normalized = stripBom(content.replace("\r\n", "\n").replace('\r', '\n'));
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "srt" -> parseSrt(normalized);
            case "vtt" -> parseVtt(normalized);
            case "ass", "ssa" -> parseAss(normalized);
            default -> throw new BizException(ResultCode.FORMAT_NOT_SUPPORTED, "不支持的字幕源格式: " + ext);
        };
    }

    public static String write(List<SubtitleCue> cues, String ext) {
        return switch (ext.toLowerCase(Locale.ROOT)) {
            case "srt" -> writeSrt(cues);
            case "vtt" -> writeVtt(cues);
            case "ass" -> writeAss(cues, false);
            case "ssa" -> writeAss(cues, true);
            default -> throw new BizException(ResultCode.FORMAT_NOT_SUPPORTED, "不支持的字幕目标格式: " + ext);
        };
    }

    private static List<SubtitleCue> parseSrt(String content) {
        List<SubtitleCue> cues = new ArrayList<>();
        for (String block : content.split("\n{2,}")) {
            String[] lines = block.strip().split("\n");
            int timingIndex = findTimingLine(lines);
            if (timingIndex < 0) {
                continue;
            }
            long[] range = parseRange(lines[timingIndex], false);
            if (range == null) {
                continue;
            }
            String text = joinLines(lines, timingIndex + 1);
            addCue(cues, range[0], range[1], text);
        }
        return cues;
    }

    private static List<SubtitleCue> parseVtt(String content) {
        List<SubtitleCue> cues = new ArrayList<>();
        for (String block : content.split("\n{2,}")) {
            String trimmed = block.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("WEBVTT") || trimmed.startsWith("NOTE")) {
                continue;
            }
            String[] lines = trimmed.split("\n");
            int timingIndex = findTimingLine(lines);
            if (timingIndex < 0) {
                continue;
            }
            long[] range = parseRange(lines[timingIndex], false);
            if (range == null) {
                continue;
            }
            String text = joinLines(lines, timingIndex + 1);
            addCue(cues, range[0], range[1], text);
        }
        return cues;
    }

    private static List<SubtitleCue> parseAss(String content) {
        List<SubtitleCue> cues = new ArrayList<>();
        boolean inEvents = false;
        int startIdx = 1;
        int endIdx = 2;
        int textIdx = 9;
        int fieldCount = 10;

        for (String raw : content.split("\n")) {
            String line = raw.strip();
            if (line.startsWith("[") && line.endsWith("]")) {
                inEvents = line.equalsIgnoreCase("[Events]");
                continue;
            }
            if (!inEvents) {
                continue;
            }
            if (line.toLowerCase(Locale.ROOT).startsWith("format:")) {
                String[] fields = line.substring(7).split(",");
                fieldCount = fields.length;
                Map<String, Integer> index = new LinkedHashMap<>();
                for (int i = 0; i < fields.length; i++) {
                    index.put(fields[i].trim().toLowerCase(Locale.ROOT), i);
                }
                startIdx = index.getOrDefault("start", 1);
                endIdx = index.getOrDefault("end", 2);
                textIdx = index.getOrDefault("text", fieldCount - 1);
                continue;
            }
            if (!line.toLowerCase(Locale.ROOT).startsWith("dialogue:")) {
                continue;
            }
            String body = line.substring("dialogue:".length());
            String[] parts = body.split(",", fieldCount);
            if (parts.length <= Math.max(startIdx, Math.max(endIdx, textIdx))) {
                continue;
            }
            Long start = parseAssTime(parts[startIdx].trim());
            Long end = parseAssTime(parts[endIdx].trim());
            if (start == null || end == null) {
                continue;
            }
            String text = parts[textIdx]
                    .replace("\\N", "\n")
                    .replace("\\n", "\n")
                    .replace("\\h", " ");
            text = ASS_TAG.matcher(text).replaceAll("");
            addCue(cues, start, end, text);
        }
        return cues;
    }

    private static int findTimingLine(String[] lines) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("-->")) {
                return i;
            }
        }
        return -1;
    }

    private static long[] parseRange(String timingLine, boolean ass) {
        String[] tokens = timingLine.trim().split("\\s+");
        if (tokens.length < 3) {
            return null;
        }
        Long start = ass ? parseAssTime(tokens[0]) : parseSrtTime(tokens[0]);
        Long end = ass ? parseAssTime(tokens[2]) : parseSrtTime(tokens[2]);
        if (start == null || end == null || end <= start) {
            return null;
        }
        return new long[]{start, end};
    }

    private static Long parseSrtTime(String value) {
        Matcher m = SRT_TIME.matcher(value);
        if (!m.find()) {
            return null;
        }
        long ms = Integer.parseInt(m.group(4));
        if (m.group(4).length() == 1) {
            ms *= 100;
        } else if (m.group(4).length() == 2) {
            ms *= 10;
        }
        return ((Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2))) * 60
                + Long.parseLong(m.group(3))) * 1000 + ms;
    }

    private static Long parseAssTime(String value) {
        Matcher m = ASS_TIME.matcher(value);
        if (!m.find()) {
            return null;
        }
        long centis = m.group(4).length() == 1
                ? Long.parseLong(m.group(4)) * 10
                : Long.parseLong(m.group(4));
        return ((Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2))) * 60
                + Long.parseLong(m.group(3))) * 1000 + centis * 10;
    }

    private static String joinLines(String[] lines, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < lines.length; i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString().strip();
    }

    private static void addCue(List<SubtitleCue> cues, long start, long end, String text) {
        if (text != null && !text.isBlank()) {
            cues.add(new SubtitleCue(start, end, text));
        }
    }

    private static String writeSrt(List<SubtitleCue> cues) {
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (SubtitleCue cue : cues) {
            sb.append(index++).append('\n');
            sb.append(formatSrtTime(cue.getStartMs())).append(" --> ")
                    .append(formatSrtTime(cue.getEndMs())).append('\n');
            sb.append(cue.getText().replace("\n", "\r\n")).append("\n\n");
        }
        return sb.toString();
    }

    private static String writeVtt(List<SubtitleCue> cues) {
        StringBuilder sb = new StringBuilder("WEBVTT\n\n");
        for (SubtitleCue cue : cues) {
            sb.append(formatVttTime(cue.getStartMs())).append(" --> ")
                    .append(formatVttTime(cue.getEndMs())).append('\n');
            sb.append(cue.getText()).append("\n\n");
        }
        return sb.toString();
    }

    private static String writeAss(List<SubtitleCue> cues, boolean ssa) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Script Info]\n");
        sb.append("ScriptType: ").append(ssa ? "v4.00" : "v4.00+").append('\n');
        sb.append("PlayResX: 1920\nPlayResY: 1080\n\n");
        sb.append("[V4+ Styles]\n");
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, "
                + "Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, "
                + "Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n");
        sb.append("Style: Default,微软雅黑,54,&H00FFFFFF,&H000000FF,&H00000000,&H64000000,"
                + "0,0,0,0,100,100,0,0,1,2,1,2,20,20,20,1\n\n");
        sb.append("[Events]\n");
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");
        for (SubtitleCue cue : cues) {
            sb.append("Dialogue: 0,")
                    .append(formatAssTime(cue.getStartMs())).append(',')
                    .append(formatAssTime(cue.getEndMs())).append(",Default,,0,0,0,,")
                    .append(cue.getText().replace("\n", "\\N"))
                    .append('\n');
        }
        return sb.toString();
    }

    private static String formatSrtTime(long ms) {
        long h = ms / 3_600_000;
        long m = (ms % 3_600_000) / 60_000;
        long s = (ms % 60_000) / 1000;
        long millis = ms % 1000;
        return String.format("%02d:%02d:%02d,%03d", h, m, s, millis);
    }

    private static String formatVttTime(long ms) {
        return formatSrtTime(ms).replace(',', '.');
    }

    private static String formatAssTime(long ms) {
        long h = ms / 3_600_000;
        long m = (ms % 3_600_000) / 60_000;
        long s = (ms % 60_000) / 1000;
        long centis = (ms % 1000) / 10;
        return String.format("%d:%02d:%02d.%02d", h, m, s, centis);
    }

    private static String stripBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
