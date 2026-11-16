package com.campus.convert.service;

import com.campus.convert.domain.dto.FormatCatalogResponse;
import com.campus.convert.domain.dto.FormatOption;
import com.campus.convert.domain.entity.FormatTemplate;
import com.campus.convert.engine.ConversionEngine;
import com.campus.convert.engine.EngineType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 格式能力目录：把 format_template 与已注册引擎组合成前端可消费的"能力清单"，
 * 供多格式转换 API 展示"源格式 → 目标格式 + 引擎 + 参数 Schema + 是否可用"。
 */
@Service
@RequiredArgsConstructor
public class FormatCatalogService {

    private static final Set<String> AUDIO = Set.of("flac", "wav", "m4a", "mp3", "ogg", "aac", "wma", "ncm");
    private static final Set<String> IMAGE = Set.of("png", "jpg", "jpeg", "heic", "heif", "webp", "bmp", "tiff", "gif");
    private static final Set<String> VIDEO = Set.of("m4s", "mov", "hevc", "mkv", "avi", "mp4", "flv", "wmv");
    private static final Set<String> SUBTITLE = Set.of("srt", "vtt", "ass", "ssa", "sub");
    private static final Set<String> DOCUMENT = Set.of(
            "doc", "docx", "ppt", "pptx", "pdf", "pages", "caj", "djvu", "djv", "txt", "md", "xls", "xlsx");

    private final FormatTemplateService formatTemplateService;
    private final List<ConversionEngine> engines;
    private final ObjectMapper objectMapper;

    public FormatCatalogResponse catalog() {
        List<FormatOption> formats = listAll();
        Map<String, List<FormatOption>> grouped = formats.stream()
                .collect(Collectors.groupingBy(FormatOption::getCategory,
                        LinkedHashMap::new, Collectors.toList()));
        FormatCatalogResponse response = new FormatCatalogResponse();
        response.setTotal(formats.size());
        response.setFormats(formats);
        response.setGrouped(grouped);
        return response;
    }

    public List<FormatOption> listAll() {
        return formatTemplateService.listAll().stream()
                .filter(t -> t.getEnabled() != null && t.getEnabled() == 1)
                .map(this::toOption)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(FormatOption::getCategory)
                        .thenComparing(FormatOption::getSourceExt)
                        .thenComparing(FormatOption::getTargetExt))
                .toList();
    }

    public List<FormatOption> listBySource(String sourceExt) {
        String source = normalize(sourceExt);
        return listAll().stream()
                .filter(option -> option.getSourceExt().equals(source))
                .toList();
    }

    private FormatOption toOption(FormatTemplate template) {
        EngineType type;
        try {
            type = EngineType.fromCode(template.getEngine());
        } catch (IllegalArgumentException e) {
            return null;
        }
        FormatOption option = new FormatOption();
        option.setSourceExt(normalize(template.getSourceExt()));
        option.setTargetExt(normalize(template.getTargetExt()));
        option.setEngine(type.getCode());
        option.setCategory(category(type, option.getSourceExt()));
        option.setParamsSchema(parseSchema(template.getParamsSchema()));
        option.setAvailable(engines.stream().anyMatch(engine ->
                engine.getType() == type
                        && engine.supports(option.getSourceExt(), option.getTargetExt())));
        return option;
    }

    private String category(EngineType type, String sourceExt) {
        if (type == EngineType.FORMULA_OCR) {
            return "ocr";
        }
        if (SUBTITLE.contains(sourceExt)) {
            return "subtitle";
        }
        if (VIDEO.contains(sourceExt)) {
            return "video";
        }
        if (AUDIO.contains(sourceExt)) {
            return "audio";
        }
        if (IMAGE.contains(sourceExt)) {
            return "image";
        }
        if (DOCUMENT.contains(sourceExt)) {
            return "document";
        }
        return "other";
    }

    private Map<String, Object> parseSchema(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String normalize(String ext) {
        return ext == null ? "" : ext.trim().toLowerCase(Locale.ROOT).replace(".", "");
    }
}
