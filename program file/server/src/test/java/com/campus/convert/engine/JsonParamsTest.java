package com.campus.convert.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonParamsTest {

    @Test
    void readsStringAndInteger() {
        String json = "{\"bitrate\":\"320k\",\"quality\":95}";
        assertEquals("320k", JsonParams.string(json, "bitrate", "192k"));
        assertEquals(95, JsonParams.integer(json, "quality", 80));
    }

    @Test
    void fallsBackWhenMissingOrInvalid() {
        assertEquals("192k", JsonParams.string(null, "bitrate", "192k"));
        assertEquals("192k", JsonParams.string("not-json", "bitrate", "192k"));
        assertEquals(80, JsonParams.integer("{\"quality\":\"bad\"}", "quality", 80));
        assertEquals(80, JsonParams.integer("{}", "quality", 80));
    }
}
