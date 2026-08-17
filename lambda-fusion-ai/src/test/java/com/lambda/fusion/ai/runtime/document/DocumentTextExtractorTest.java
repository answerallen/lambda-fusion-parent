package com.lambda.fusion.ai.runtime.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentTextExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsLongTextWithoutReaderOverlapDuplication() throws Exception {
        String content = "设备通讯协议正文".repeat(200);
        Path file = tempDir.resolve("protocol.txt");
        Files.writeString(file, content);

        assertThat(DocumentTextExtractor.extractText("txt", file)).isEqualTo(content);
    }
}
