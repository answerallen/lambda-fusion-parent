package com.lambda.fusion.ai.rag.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lambda.fusion.ai.AiConstants.DocumentChunkStrategy;
import com.lambda.fusion.ai.AiProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentChunkerTest {

    @Test
    void autoKeepsShortDocumentWhole() {
        AiProperties.Rag.Chunking options = options(64, 8, 100);

        List<IngestChunk> chunks =
                DocumentChunker.chunk("doc1", "这是一份短小且语义完整的说明。", DocumentChunkStrategy.AUTO, options);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().text()).isEqualTo("这是一份短小且语义完整的说明。");
        assertThat(chunks.getFirst().chunkId()).isEqualTo("doc1-0");
        assertThat(chunks.getFirst().sectionPath()).isNull();
    }

    @Test
    void autoUsesHeadingHierarchyForStructuredDocument() {
        AiProperties.Rag.Chunking options = options(96, 8, 20);
        String text =
                """
                第一章 总则
                本章介绍协议用途和适用范围。

                1.1 通讯参数
                波特率为 9600，数据位为 8。

                第二章 报文格式
                请求报文由地址、功能码、数据和校验码组成。
                """;

        List<IngestChunk> chunks = DocumentChunker.chunk("doc2", text, DocumentChunkStrategy.AUTO, options);

        assertThat(chunks).extracting(IngestChunk::sectionPath).contains("第一章 总则", "第一章 总则 / 1.1 通讯参数", "第二章 报文格式");
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.text()).contains(chunk.sectionPath()));
    }

    @Test
    void headingStrategyRecursivelySplitsOversizedSection() {
        AiProperties.Rag.Chunking options = options(80, 10, 20);
        String text = "第二章 数据定义\n" + "保持寄存器用于保存设备运行参数。".repeat(20);

        List<IngestChunk> chunks = DocumentChunker.chunk("doc3", text, DocumentChunkStrategy.HEADING, options);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.sectionPath()).isEqualTo("第二章 数据定义");
            assertThat(chunk.text().length()).isLessThanOrEqualTo(80);
        });
        for (int index = 0; index < chunks.size(); index++) {
            assertThat(chunks.get(index).chunkIndex()).isEqualTo(index);
            assertThat(chunks.get(index).chunkId()).isEqualTo("doc3-" + index);
        }
    }

    @Test
    void autoDoesNotTreatProtocolValuesAsNumberedHeadings() {
        AiProperties.Rag.Chunking options = options(64, 8, 20);
        String text = "9600 波特率\n8 数据位\n1 停止位\n" + "普通协议说明。".repeat(20);

        assertThat(DocumentChunker.resolveStrategy(text, DocumentChunkStrategy.AUTO, options))
                .isEqualTo(DocumentChunkStrategy.PARAGRAPH);
    }

    @Test
    void rejectsOverlapNotSmallerThanChunkSize() {
        AiProperties.Rag.Chunking options = options(50, 50, 100);

        assertThatThrownBy(() -> DocumentChunker.chunk("doc4", "content", DocumentChunkStrategy.PARAGRAPH, options))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Overlap size");
    }

    private static AiProperties.Rag.Chunking options(int chunkSize, int overlapSize, int wholeMaxChars) {
        AiProperties.Rag.Chunking options = new AiProperties.Rag.Chunking();
        options.setChunkSize(chunkSize);
        options.setOverlapSize(overlapSize);
        options.setWholeDocumentMaxChars(wholeMaxChars);
        return options;
    }
}
