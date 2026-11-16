package com.campus.convert.engine.ncm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class NcmDecoderTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsFileWithWrongMagic() throws Exception {
        Path fake = tempDir.resolve("fake.ncm");
        Files.writeString(fake, "NOT-A-NCM-FILE");
        assertThrows(IOException.class, () -> NcmDecoder.decrypt(fake, tempDir));
    }

    @Test
    void rejectsTruncatedHeader() throws Exception {
        Path truncated = tempDir.resolve("truncated.ncm");
        Files.write(truncated, new byte[]{'C', 'T', 'E', 'N', 'F', 'D'});
        assertThrows(IOException.class, () -> NcmDecoder.decrypt(truncated, tempDir));
    }
}
