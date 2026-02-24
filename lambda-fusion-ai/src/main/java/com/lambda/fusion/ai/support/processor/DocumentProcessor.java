package com.lambda.fusion.ai.support.processor;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.lambda.fusion.ai.AiConstants.Enums.DocumentStatus;
import com.lambda.fusion.ai.mapper.DocumentChunkMapper;
import com.lambda.fusion.ai.mapper.DocumentMapper;
import com.lambda.fusion.ai.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.model.entity.DocumentChunkEntity;
import com.lambda.fusion.ai.model.entity.DocumentEntity;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.repository.VectorRepository;
import com.lambda.fusion.autoconfig.AiProperties;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 文档处理管道
 * 负责文档的加载、切分、向量化和存储
 *
 * @author Jin
 */
@SuppressWarnings("all")
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentProcessor {

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final VectorRepository vectorRepository;
    private final EmbeddingModel embeddingModel;
    private final AiProperties aiProperties;
    private final TransactionTemplate transactionTemplate;

    @Async("documentProcessExecutor")
    public void processDocument(Long documentId) {
        log.info("开始处理文档: {}", documentId);
        DocumentEntity doc = documentMapper.selectById(documentId);
        if (doc == null) {
            log.error("文档不存在: {}", documentId);
            return;
        }

        try {
            // 1. 更新状态为处理中
            updateStatus(doc, DocumentStatus.PROCESSING, 0, null);

            // 2. 加载文档内容
            String content = loadContent(doc);
            if (content == null || content.isEmpty()) {
                throw new RuntimeException("文档内容为空");
            }
            updateStatus(doc, DocumentStatus.PROCESSING, 20, "内容加载完成");

            // 3. 获取知识库配置
            KnowledgeBaseEntity kb = knowledgeBaseMapper.selectById(doc.getKbId());
            if (kb == null) {
                throw new RuntimeException("知识库不存在: " + doc.getKbId());
            }
            Integer embeddingDimension = kb.getEmbeddingDimension();

            // 4. 文档切分 - 使用配置验证
            int chunkSize = aiProperties.getDocumentChunk().getValidatedChunkSize(kb.getChunkSize());
            int chunkOverlap =
                    aiProperties.getDocumentChunk().getValidatedChunkOverlap(kb.getChunkOverlap(), chunkSize);

            log.info("使用配置进行文档切分 - ChunkSize: {}, ChunkOverlap: {}", chunkSize, chunkOverlap);

            DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
            List<TextSegment> segments = splitter.split(Document.from(content));
            updateStatus(doc, DocumentStatus.PROCESSING, 40, "文档切分完成: " + segments.size() + "块");

            // 5. 批量处理向量和分片
            List<DocumentChunkEntity> chunkEntities = new ArrayList<>();
            int totalChunks = segments.size();

            for (int i = 0; i < totalChunks; i++) {
                TextSegment segment = segments.get(i);

                // 向量化
                Response<Embedding> embeddingResponse = embeddingModel.embed(segment);
                if (embeddingResponse == null || embeddingResponse.content() == null) {
                    throw new RuntimeException("向量化失败: 返回结果为空");
                }

                List<Double> vector = embeddingResponse.content().vectorAsList().stream()
                        .map(Float::doubleValue)
                        .collect(Collectors.toList());

                // 构建实体
                DocumentChunkEntity chunk = new DocumentChunkEntity();
                chunk.setChunkId(IdUtil.fastSimpleUUID());
                chunk.setDocumentId(doc.getId());
                chunk.setKbId(kb.getId());
                chunk.setContent(segment.text());
                chunk.setChunkIndex(i);
                chunk.setEmbeddingStatus("COMPLETED");
                chunk.setVectorId(IdUtil.fastSimpleUUID());
                chunk.setCharCount(segment.text().length());
                chunk.setMetadata(JSONUtil.toJsonStr(segment.metadata()));
                chunk.setEmbedding(vector);

                chunkEntities.add(chunk);

                // 每批次更新一次进度
                if (i % aiProperties.getDocumentChunk().getBatchSize() == 0) {
                    int progress = 40 + (int) ((double) i / totalChunks * 50);
                    updateStatus(
                            doc, DocumentStatus.PROCESSING, progress, "向量化处理中 (" + (i + 1) + "/" + totalChunks + ")");
                }
            }

            // 6. 执行批量插入 - 在单一事务中完成
            if (!chunkEntities.isEmpty()) {
                log.info("执行批量存储: {} chunks", chunkEntities.size());

                transactionTemplate.execute(status -> {
                    try {
                        documentChunkMapper.batchInsert(chunkEntities);
                        vectorRepository.batchInsertVectorsUnified(chunkEntities, kb.getId(), embeddingDimension);
                        return null;
                    } catch (Exception e) {
                        log.error("批量插入失败，事务将回滚", e);
                        status.setRollbackOnly();
                        throw new RuntimeException("批量插入失败", e);
                    }
                });
            }

            // 7. 更新文档统计信息和最终状态
            doc.setChunkCount(totalChunks);
            doc.setVectorCount(totalChunks);
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
        }
    }

    private String loadContent(DocumentEntity doc) {
        if ("LOCAL".equals(doc.getStorageType())) {
            File file = new File(doc.getStorageUrl());
            if (!file.exists()) {
                throw new RuntimeException("文件不存在: " + doc.getStorageUrl());
            }
            Document document = FileSystemDocumentLoader.loadDocument(file.toPath());
            return document.text();
        }
        throw new UnsupportedOperationException("暂不支持的存储类型: " + doc.getStorageType());
    }

    private void updateStatus(DocumentEntity doc, DocumentStatus status, int progress, String msg) {
        doc.setProcessStatus(status.name());
        doc.setProcessProgress(progress);
        doc.setErrorMessage(msg);
        documentMapper.updateProcessStatus(doc.getId(), status.name(), progress, msg);
    }
}
