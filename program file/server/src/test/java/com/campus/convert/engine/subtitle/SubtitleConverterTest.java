package com.campus.convert.engine.subtitle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubtitleConverterTest {

    @Test
    void srtToVtt() {
        String srt = "1\n00:00:01,000 --> 00:00:03,500\n你好\n世界\n\n"
                + "2\n00:00:04,000 --> 00:00:05,000\n第二句\n";
        List<SubtitleCue> cues = SubtitleConverter.parse(srt, "srt");
        assertEquals(2, cues.size());
        assertEquals(1000, cues.get(0).getStartMs());
        assertEquals(3500, cues.get(0).getEndMs());
        assertEquals("你好\n世界", cues.get(0).getText());

        String vtt = SubtitleConverter.write(cues, "vtt");
        assertTrue(vtt.startsWith("WEBVTT"));
        assertTrue(vtt.contains("00:00:01.000 --> 00:00:03.500"));
        assertTrue(vtt.contains("你好\n世界"));
    }

    @Test
    void assToSrt() {
        String ass = "[Script Info]\nScriptType: v4.00+\n\n[Events]\n"
                + "Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
                + "Dialogue: 0,0:00:01.00,0:00:03.50,Default,,0,0,0,,{\\b1}你好\\N世界\n";
        List<SubtitleCue> cues = SubtitleConverter.parse(ass, "ass");
        assertEquals(1, cues.size());
        assertEquals(1000, cues.get(0).getStartMs());
        assertEquals(3500, cues.get(0).getEndMs());
        assertEquals("你好\n世界", cues.get(0).getText());

        String srt = SubtitleConverter.write(cues, "srt");
        assertTrue(srt.contains("00:00:01,000 --> 00:00:03,500"));
    }

    @Test
    void vttToAss() {
        String vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHello\n";
        List<SubtitleCue> cues = SubtitleConverter.parse(vtt, "vtt");
        assertEquals(1, cues.size());
        String ass = SubtitleConverter.write(cues, "ass");
        assertTrue(ass.contains("[Events]"));
        assertTrue(ass.contains("Dialogue: 0,0:00:01.00,0:00:02.00"));
        assertTrue(ass.contains("Hello"));
    }
}
