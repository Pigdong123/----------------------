package com.campus.convert.engine.subtitle;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通用字幕条目：时间轴 + 文本
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubtitleCue {

    private long startMs;
    private long endMs;
    private String text;
}
