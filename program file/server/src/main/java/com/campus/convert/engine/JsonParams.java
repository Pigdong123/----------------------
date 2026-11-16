package com.campus.convert.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 转换参数解析工具：从任务 params(JSON) 中安全读取字段，解析失败回退默认值。
 * <p>
 * 审计修复：原三个引擎的 extractXxx() 均为 TODO，参数配置实际不生效。
 */
public final class JsonParams {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonParams() {
    }

    public static String string(String json, String field, String defaultValue) {
        JsonNode node = read(json);
        if (node == null) {
            return defaultValue;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText(defaultValue);
    }

    public static int integer(String json, String field, int defaultValue) {
        JsonNode node = read(json);
        if (node == null) {
            return defaultValue;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asInt(defaultValue);
    }

    private static JsonNode read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }
}
