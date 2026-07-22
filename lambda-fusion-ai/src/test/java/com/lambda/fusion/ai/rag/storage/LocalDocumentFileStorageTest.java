package com.lambda.fusion.ai.rag.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.lambda.fusion.ai.AiProperties;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 验证 {@link LocalDocumentFileStorage}：store 落到预期相对路径、download 内容一致、
 * delete 删除文件且重复删不报错。
 *
 * @author Jin
 */
class LocalDocumentFileStorageTest {

    @Test
    void storeDownloadDeleteRoundTrip(@TempDir Path tempDir) throws Exception {
        AiProperties props = new AiProperties();
        props.getRag().getDocumentStorage().getLocal().setRoot(tempDir.toString());
        LocalDocumentFileStorage storage = new LocalDocumentFileStorage(props);
        Path source = Files.createTempFile("kb-src-", ".txt");
        try {
            Files.writeString(source, "知识库文档内容", StandardCharsets.UTF_8);

            String storagePath = storage.store(source, "t1/kb1/doc1.txt");

            assertThat(storagePath).isEqualTo("t1/kb1/doc1.txt");
            Path stored = tempDir.resolve("t1/kb1/doc1.txt");
            assertThat(stored).exists();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            storage.download(storagePath, out);
            assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("知识库文档内容");

            storage.delete(storagePath);
            assertThat(stored).doesNotExist();
            // 重复删除不报错
            assertThatCode(() -> storage.delete(storagePath)).doesNotThrowAnyException();
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void rootFallsBackToWorkspaceRoot(@TempDir Path tempDir) throws Exception {
        AiProperties props = new AiProperties();
        props.getWorkspace().setRoot(tempDir.resolve("ws").toString());
        LocalDocumentFileStorage storage = new LocalDocumentFileStorage(props);
        Path source = Files.createTempFile("kb-src-", ".md");
        try {
            Files.writeString(source, "# md", StandardCharsets.UTF_8);

            String storagePath = storage.store(source, "t1/kb2/doc2.md");

            // 未显式配置 local.root 时回落 {workspace.root}/knowledge-files
            assertThat(tempDir.resolve("ws/knowledge-files").resolve(storagePath))
                    .exists();
        } finally {
            Files.deleteIfExists(source);
        }
    }
}
