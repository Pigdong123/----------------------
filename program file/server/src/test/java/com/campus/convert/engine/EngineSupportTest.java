package com.campus.convert.engine;

import com.campus.convert.common.BizException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineSupportTest {

    @TempDir
    Path tempDir;

    @Test
    void ensureParentCreatesDirectories() {
        Path target = tempDir.resolve("a/b/c/out.webp");
        EngineSupport.ensureParent(target.toString());
        assertTrue(Files.isDirectory(target.getParent()));
    }

    @Test
    void ensureOutputRejectsMissingAndEmpty() throws Exception {
        assertThrows(BizException.class, () -> EngineSupport.ensureOutput(
                tempDir.resolve("missing.pdf").toString(), "test"));

        Path empty = tempDir.resolve("empty.pdf");
        Files.createFile(empty);
        assertThrows(BizException.class, () -> EngineSupport.ensureOutput(empty.toString(), "test"));
    }

    @Test
    void ensureOutputAcceptsNonEmptyFile() throws Exception {
        Path ok = tempDir.resolve("ok.pdf");
        Files.write(ok, "%PDF-1.7".getBytes());
        assertDoesNotThrow(() -> EngineSupport.ensureOutput(ok.toString(), "test"));
    }

    @Test
    void baseNameStripsExtension() {
        assertTrue(EngineSupport.baseName("C:/x/y/课件 v1.docx").equals("课件 v1"));
    }
}
