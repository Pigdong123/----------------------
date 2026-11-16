package com.campus.convert.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 文件魔数识别测试：确保扩展名伪造无法绕过真实类型判断。
 */
class FileTypeDetectorTest {

    @Test
    void detectsPngByMagic() throws Exception {
        byte[] header = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "fake.txt", "text/plain", header);
        assertEquals("png", FileTypeDetector.detectExtension(file));
    }

    @Test
    void detectsJpegByMagic() throws Exception {
        byte[] header = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "fake.txt", "text/plain", header);
        assertEquals("jpg", FileTypeDetector.detectExtension(file));
    }

    @Test
    void detectsWavByRiffWave() throws Exception {
        byte[] header = new byte[16];
        System.arraycopy("RIFF".getBytes(), 0, header, 0, 4);
        System.arraycopy("WAVE".getBytes(), 0, header, 8, 4);
        MockMultipartFile file = new MockMultipartFile("file", "a.wav", "audio/wav", header);
        assertEquals("wav", FileTypeDetector.detectExtension(file));
    }

    @Test
    void returnsNullForUnknownMagic() throws Exception {
        byte[] header = "hello world!!".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "a.docx", "application/octet-stream", header);
        assertNull(FileTypeDetector.detectExtension(file));
    }
}
