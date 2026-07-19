package com.lambda.fusion.ai.knowledge.processor;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.lambda.cloud.oss.client.OssClient;
import com.lambda.cloud.oss.manager.OssClientManager;
import com.lambda.fusion.ai.AiConstants.Enums.DocumentStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.agent.runtime.KnowledgeFactory;
import com.lambda.fusion.ai.knowledge.mapper.DocumentChunkMapper;
import com.lambda.fusion.ai.knowledge.mapper.DocumentMapper;
import com.lambda.fusion.ai.knowledge.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.knowledge.model.entity.DocumentChunkEntity;
import com.lambda.fusion.ai.knowledge.model.entity.DocumentEntity;
import com.lambda.fusion.ai.knowledge.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.datasource.api.DataSourceSwitcher;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.DocumentMetadata;
import io.agentscope.core.rag.reader.PDFReader;
import io.agentscope.core.rag.reader.Reader;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.reader.TikaReader;
import io.agentscope.core.rag.reader.WordReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 文档处理管道（AgentScope 重构版）。
 *
 * <p>引擎从自研 pgvector（langchain4j 解析+分块+embedAll + 维度分表 VectorRepository）改为 AgentScope：
 * <ol>
 *   <li>加载文档（LOCAL/OSS）-> {@link ReaderInput}；</li>
 *   <li>{@link Reader}（PDFReader/WordReader/TikaReader/TextReader，按 KB chunkSize/overlap/strategy）解析+分块
 *       -> {@code List<Document>}；</li>
 *   <li>钉住 {@code DocumentMetadata.docId = 文档ID}（供 {@link com.lambda.fusion.ai.agent.runtime.VectorStoreOps}
 *       按 doc_id 删除）；</li>
 *   <li>{@link KnowledgeFactory#get} 的 {@code SimpleKnowledge.addDocuments} 批量 embed+store 到 PgVectorStore
 *       （表 {@code ai_vector_store_<dim>}，含 doc_id 列）；</li>
 *   <li>同步写 {@link DocumentChunkEntity} 元数据（content/chunkIndex/charCount，供 pageChunks 展示；
 *       向量本身在 PgVectorStore，不入此表）。</li>
 * </ol>
 *
 * <p>清零 langchain4j（6 处耦合之一）；维度分表/归一化（{@code VectorDimensionProcessor}）随 pgvector 管线删除。
 *
 * @author Jin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessor {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeFactory knowledgeFactory;
    private final AiProperties aiProperties;
    private final TransactionTemplate transactionTemplate;

    private OssClientManager ossClientManager;

    @Autowired
    public void setOssClientManager(OssClientManager ossClientManager) {
        this.ossClientManager = ossClientManager;
    }

    @Async("documentProcessExecutor")
    public void processDocument(String documentId) {
        try (DataSourceSwitcher ignored = switchToProcessingDataSource()) {
            processDocumentInCurrentDataSource(documentId);
        }
    }

    private void processDocumentInCurrentDataSource(String documentId) {
        log.info("开始处理文档: {}", documentId);
        DocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.error("文档不存在: {}", documentId);
            return;
        }

        File tempFile = null;
        try {
            updateStatus(doc, DocumentStatus.PROCESSING, 10, null);

            KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(doc.getKbId());
            if (kb == null) {
                throw new RuntimeException("知识库不存在: " + doc.getKbId());
            }

            // 1. 加载文档到 ReaderInput（OSS 下载到临时文件）
            tempFile = downloadToTempIfOss(doc);
            ReaderInput input = tempFile != null
                    ? ReaderInput.fromFile(tempFile)
                    : ReaderInput.fromFile(new File(doc.getStorageUrl()));
            updateStatus(doc, DocumentStatus.PROCESSING, 20, "内容加载完成");

            // 2. Reader 解析+分块（按 KB chunk 配置）
            int chunkSize = aiProperties.getDocumentChunk().getValidatedChunkSize(kb.getChunkSize());
            int chunkOverlap = aiProperties.getDocumentChunk().getValidatedChunkOverlap(kb.getChunkOverlap(), chunkSize);
            SplitStrategy strategy = mapStrategy(kb.getChunkStrategy());
            Reader reader = createReader(doc.getFileType(), chunkSize, strategy, chunkOverlap);

            List<Document> chunks = reader.read(input).block();
            if (chunks == null || chunks.isEmpty()) {
                throw new RuntimeException("文档解析/分块结果为空");
            }
            updateStatus(doc, DocumentStatus.PROCESSING, 40, "分块完成: " + chunks.size() + "块");

            // 3. 钉住 docId（供 VectorStoreOps 按 doc_id 删除）+ 写 DocumentChunkEntity 元数据
            List<Document> tagged = new ArrayList<>(chunks.size());
            List<DocumentChunkEntity> chunkEntities = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                Document orig = chunks.get(i);
                DocumentMetadata origMeta = orig.getMetadata();
                String chunkId = origMeta != null && StrUtil.isNotBlank(origMeta.getChunkId())
                        ? origMeta.getChunkId()
                        : IdUtil.fastSimpleUUID();
                String contentText = origMeta != null ? origMeta.getContentText() : "";

                DocumentMetadata taggedMeta = DocumentMetadata.builder()
                        .content(origMeta != null ? origMeta.getContent() : null)
                        .chunkId(chunkId)
                        .docId(documentId)
                        .payload(origMeta != null ? origMeta.getPayload() : null)
                        .build();
                tagged.add(new Document(taggedMeta));

                DocumentChunkEntity chunk = new DocumentChunkEntity();
                chunk.setId(IdUtil.fastSimpleUUID());
                chunk.setDocumentId(documentId);
                chunk.setKbId(kb.getId());
                chunk.setContent(contentText);
                chunk.setChunkIndex(i);
                chunk.setEmbeddingStatus("COMPLETED");
                chunk.setVectorId(chunkId);
                chunk.setCharCount(contentText != null ? contentText.length() : 0);
                chunk.setTenantId(doc.getTenantId());
                chunkEntities.add(chunk);
            }

            // 4. 写 chunk 元数据（展示用，不含向量）
            if (!chunkEntities.isEmpty()) {
                transactionTemplate.executeWithoutResult(status -> documentChunkMapper.batchInsert(chunkEntities));
            }
            updateStatus(doc, DocumentStatus.PROCESSING, 60, "分块元数据已写");

            // 5. SimpleKnowledge 批量 embed + 存入 PgVectorStore（doc_id 列已钉住）
            Knowledge knowledge = knowledgeFactory.get(doc.getKbId());
            if (knowledge == null) {
                throw new RuntimeException("知识库 Knowledge 构造失败: " + doc.getKbId());
            }
            knowledge.addDocuments(tagged).block();
            log.info("文档向量化完成: {} chunks", tagged.size());

            // 6. 更新文档统计与状态
            doc.setChunkCount(chunks.size());
            doc.setVectorCount(chunks.size());
            doc.setProcessStatus(DocumentStatus.COMPLETED.name());
            doc.setProcessProgress(100);
            doc.setProcessedAt(java.time.LocalDateTime.now());
            documentMapper.updateById(doc);
            log.info("文档处理完成: {}", documentId);

        } catch (Exception e) {
            log.error("文档处理失败: {}", documentId, e);
            try {
                updateStatus(doc, DocumentStatus.FAILED, 0, e.getMessage());
            } catch (Exception updateException) {
                log.error("更新文档失败状态异常: {}", documentId, updateException);
            }
        } finally {
            if (tempFile != null && !tempFile.delete()) {
                log.warn("临时文件删除失败: {}", tempFile.getAbsolutePath());
            }
        }
    }

    private DataSourceSwitcher switchToProcessingDataSource() {
        return DataSourceSwitcher.switchTo(aiProperties.getDataSource().getName());
    }

    /** OSS 文档下载到临时文件（Reader 需文件路径）；非 OSS 返回 null。 */
    private File downloadToTempIfOss(DocumentEntity doc) {
        if (!"OSS".equals(doc.getStorageType())) {
            return null;
        }
        if (ossClientManager == null) {
            throw new RuntimeException("OSS客户端管理器未初始化");
        }
        String clientName = aiProperties.getDocument().getOssClientName();
        OssClient ossClient = ossClientManager.get(clientName);
        String objectKey = StrUtil.isNotBlank(doc.getStoragePath()) ? doc.getStoragePath() : doc.getStorageUrl();
        if (StrUtil.isBlank(objectKey)) {
            throw new RuntimeException("OSS对象键为空，无法下载文件");
        }
        try {
            byte[] bytes;
            try (var s3Object = ossClient.getObject(objectKey);
                    var in = s3Object.getObjectContent()) {
                bytes = IoUtil.readBytes(in);
            }
            Path temp = Files.createTempFile("ai-doc-", suffixFor(doc.getFileName(), doc.getFileType()));
            Files.write(temp, bytes);
            return temp.toFile();
        } catch (Exception e) {
            throw new RuntimeException("从OSS下载文档失败: " + e.getMessage(), e);
        }
    }

    private static String suffixFor(String fileName, String fileType) {
        if (StrUtil.isNotBlank(fileName) && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.'));
        }
        return fileType != null ? "." + fileType.toLowerCase() : ".bin";
    }

    private Reader createReader(String fileType, int chunkSize, SplitStrategy strategy, int overlap) {
        String t = fileType == null ? "" : fileType.toUpperCase();
        return switch (t) {
            case "PDF" -> new PDFReader(chunkSize, strategy, overlap);
            case "DOC", "DOCX" -> new WordReader(); // 无参构造（默认分块）
            case "TXT", "MD", "MARKDOWN", "JSON", "XML", "CSV", "HTML" -> new TextReader(chunkSize, strategy, overlap);
            default -> new TikaReader(); // XLS/PPT/其它：Tika 无参（默认分块）
        };
    }

    private SplitStrategy mapStrategy(String chunkStrategy) {
        if (chunkStrategy == null) {
            return SplitStrategy.TOKEN;
        }
        return switch (chunkStrategy.toUpperCase()) {
            case "PARAGRAPH", "SENTENCE" -> SplitStrategy.PARAGRAPH; // AgentScope 无 SENTENCE，回落 PARAGRAPH
            case "FIXED" -> SplitStrategy.TOKEN;
            case "SLIDING_WINDOW" -> SplitStrategy.CHARACTER;
            default -> SplitStrategy.TOKEN;
        };
    }

    private void updateStatus(DocumentEntity doc, DocumentStatus status, int progress, String msg) {
        doc.setProcessStatus(status.name());
        doc.setProcessProgress(progress);
        doc.setErrorMessage(msg);
        documentMapper.updateProcessStatus(doc.getId(), status.name(), progress, msg);
    }
}
