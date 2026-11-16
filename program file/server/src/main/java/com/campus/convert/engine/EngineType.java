package com.campus.convert.engine;

import lombok.Getter;

/**
 * 转换引擎类型（对应 format_template.engine 字段）
 */
@Getter
public enum EngineType {

    FFMPEG("FFMPEG", "音视频引擎：ffmpeg"),
    LIBREOFFICE("LIBREOFFICE", "文档引擎：LibreOffice"),
    PANDOC("PANDOC", "文档引擎：Pandoc"),
    IMAGEMAGICK("IMAGEMAGICK", "图片引擎：ImageMagick"),
    SUBTITLE("SUBTITLE", "字幕引擎：纯 Java 字幕互转"),
    NCM("NCM", "音频引擎：NCM 解密（纯 Java）"),
    PAGES("PAGES", "文档引擎：Apple Pages 转换（外部工具）"),
    CAJ("CAJ", "文档引擎：CAJ 转换（caj2pdf）"),
    DJVU("DJVU", "文档引擎：DjVu 转换（ddjvu）"),
    FORMULA_OCR("FORMULA_OCR", "公式/OCR 引擎（Tesseract / pix2tex）");

    private final String code;
    private final String description;

    EngineType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public static EngineType fromCode(String code) {
        for (EngineType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知引擎类型: " + code);
    }
}
