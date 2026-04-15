package com.lambda.fusion.ai.commons.support.processor;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.lambda.cloud.oss.client.OssClient;
import com.lambda.cloud.oss.manager.OssClientManager;
import com.lambda.fusion.ai.AiConstants.Enums.DocumentStatus;
import com.lambda.fusion.ai.AiProperties;
import com.lambda.fusion.ai.commons.datasource.TenantDataSourceHelper;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.exception.AiErrorCode;
import com.lambda.fusion.ai.commons.support.embedding.EmbeddingModelManager;
import com.lambda.fusion.ai.commons.support.vector.VectorDimensionProcessor;
import com.lambda.fusion.ai.commons.utils.BatchProcessor;
import com.lambda.fusion.ai.mapper.DocumentChunkMapper;
import com.lambda.fusion.ai.mapper.DocumentMapper;
import com.lambda.fusion.ai.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.mapper.VectorRepository;
import com.lambda.fusion.ai.model.entity.DocumentChunkEntity;
import com.lambda.fusion.ai.model.entity.DocumentEntity;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.datasource.commons.api.DataSourceSwitcher;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final EmbeddingModelManager embeddingModelManager;
    private final AiProperties aiProperties;
    private final TransactionTemplate transactionTemplate;
    private final VectorDimensionProcessor vectorDimensionProcessor;

    private OssClientManager ossClientManager;
    private TenantDataSourceHelper tenantDataSourceHelper;

    @Autowired
    public void setOssClientManager(OssClientManager ossClientManager) {
        this.ossClientManager = ossClientManager;
    }

    @Autowired(required = false)
    public void setTenantDataSourceHelper(TenantDataSourceHelper tenantDataSourceHelper) {
        this.tenantDataSourceHelper = tenantDataSourceHelper;
    }

    @Async("documentProcessExecutor")
    public void processDocument(String tenantId, String documentId) {
        try (DataSourceSwitcher ignored = switchToProcessingDataSource(tenantId)) {
            processDocumentInCurrentDataSource(documentId);
        }
    }

    @Async("documentProcessExecutor")
    public void processDocument(String documentId) {
        processDocument(null, documentId);
    }

    private void processDocumentInCurrentDataSource(String documentId) {
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
            if (embeddingDimension == null || embeddingDimension <= 0) {
                throw new RuntimeException("知识库向量维度配置无效: " + doc.getKbId());
            }

            // 4. 文档切分 - 使用配置验证
            int chunkSize = aiProperties.getDocumentChunk().getValidatedChunkSize(kb.getChunkSize());
            int chunkOverlap =
                    aiProperties.getDocumentChunk().getValidatedChunkOverlap(kb.getChunkOverlap(), chunkSize);

            log.info("使用配置进行文档切分 - ChunkSize: {}, ChunkOverlap: {}", chunkSize, chunkOverlap);

            DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
            List<TextSegment> segments = splitter.split(Document.from(content));
            updateStatus(doc, DocumentStatus.PROCESSING, 40, "文档切分完成: " + segments.size() + "块");

            // 5. 获取知识库配置的 EmbeddingModel
            EmbeddingModel embeddingModel = embeddingModelManager.getModelByKnowledgeBase(kb.getEmbeddingModel());
            log.info("使用 EmbeddingModel: {} 处理文档", kb.getEmbeddingModel());

            // 6. 批量向量化 - 使用 embedAll 批量接口，减少 HTTP 请求次数
            log.info("开始批量向量化 {} 个分片", segments.size());
            Response<List<Embedding>> embeddingsResponse = embeddingModel.embedAll(segments);
            if (embeddingsResponse == null || embeddingsResponse.content() == null) {
                throw new RuntimeException("批量向量化失败: 返回结果为空");
            }
            List<Embedding> embeddings = embeddingsResponse.content();
            if (embeddings.size() != segments.size()) {
                throw new RuntimeException("批量向量化失败: 返回向量数量不匹配，期望 " + segments.size() + "，实际 " + embeddings.size());
            }
            log.info("批量向量化完成，共 {} 个向量", embeddings.size());

            // 7. 构建分片实体
            List<DocumentChunkEntity> chunkEntities = new ArrayList<>();
            int totalChunks = segments.size();

            for (int i = 0; i < totalChunks; i++) {
                TextSegment segment = segments.get(i);
                Embedding embedding = embeddings.get(i);

                List<Double> originalVector = embedding.vectorAsList().stream()
                        .map(Float::doubleValue)
                        .collect(Collectors.toList());

                int actualDimension = originalVector.size();
                if (actualDimension != embeddingDimension) {
                    log.warn("向量维度不匹配: 模型输出 {} 维，知识库配置 {} 维", actualDimension, embeddingDimension);
                }

                // 获取最接近的支持维度，用于选择分表
                int storageDimension = vectorDimensionProcessor.getNearestSupportedDimension(actualDimension);

                // 如果实际维度与存储维度不同，需要归一化
                List<Double> storageVector;
                if (actualDimension != storageDimension) {
                    storageVector = vectorDimensionProcessor.normalizeToDimension(originalVector, storageDimension);
                    log.debug("向量维度从 {} 归一化到 {}", actualDimension, storageDimension);
                } else {
                    storageVector = originalVector;
                }

                // 构建实体
                DocumentChunkEntity chunk = new DocumentChunkEntity();
                chunk.setId(IdUtil.fastSimpleUUID());
                chunk.setDocumentId(doc.getId());
                chunk.setKbId(kb.getId());
                chunk.setContent(segment.text());
                chunk.setChunkIndex(i);
                chunk.setEmbeddingStatus("COMPLETED");
                chunk.setVectorId(IdUtil.fastSimpleUUID());
                chunk.setCharCount(segment.text().length());
                chunk.setMetadata(JSONUtil.toJsonStr(segment.metadata()));
                chunk.setEmbedding(storageVector);
                chunk.setDimension(storageDimension);
                chunk.setTenantId(doc.getTenantId());

                chunkEntities.add(chunk);

                // 每批次更新一次进度
                if (i % aiProperties.getDocumentChunk().getBatchSize() == 0) {
                    int progress = 40 + (int) ((double) i / totalChunks * 50);
                    updateStatus(
                            doc, DocumentStatus.PROCESSING, progress, "构建分片实体 (" + (i + 1) + "/" + totalChunks + ")");
                }
            }

            // 8. 执行批量插入 - 在单一事务中完成，使用分批处理防止SQL过长
            if (!chunkEntities.isEmpty()) {
                log.info(
                        "执行批量存储: {} chunks，批次大小: {}",
                        chunkEntities.size(),
                        aiProperties.getDocumentChunk().getVectorBatchSize());

                int vectorBatchSize = aiProperties.getDocumentChunk().getVectorBatchSize();

                // 分批处理向量插入
                BatchProcessor.batchProcess(chunkEntities, vectorBatchSize, batch -> {
                    transactionTemplate.execute(status -> {
                        try {
                            documentChunkMapper.batchInsert(batch);
                            // 使用分表存储，根据维度选择表
                            Integer dimension = batch.get(0).getDimension();
                            vectorRepository.batchInsertVectors(dimension, batch, kb.getId(), "default");
                            return null;
                        } catch (Exception e) {
                            log.error("批量插入失败，事务将回滚", e);
                            status.setRollbackOnly();
                            throw new RuntimeException("批量插入失败", e);
                        }
                    });
                });
            }

            // 9. 更新文档统计信息和最终状态
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

    private DataSourceSwitcher switchToProcessingDataSource(String tenantId) {
        if (StrUtil.isNotBlank(tenantId) && !"default".equalsIgnoreCase(tenantId)) {
            if (tenantDataSourceHelper == null) {
                throw new AiBusinessException(AiErrorCode.DATASOURCE_ERROR, "租户数据源助手未启用, tenantId=" + tenantId);
            }
            return tenantDataSourceHelper.switchToResolvedDataSource(tenantId);
        }
        return DataSourceSwitcher.switchTo(aiProperties.getDataSource().getName());
    }

    private String loadContent(DocumentEntity doc) {
        String storageType = doc.getStorageType();
        if (storageType == null || storageType.isEmpty()) {
            storageType = "LOCAL";
        }

        switch (storageType.toUpperCase()) {
            case "LOCAL":
                return loadFromLocal(doc);
            case "OSS":
                return loadFromOss(doc);
            default:
                throw new UnsupportedOperationException("暂不支持的存储类型: " + storageType);
        }
    }

    private String loadFromLocal(DocumentEntity doc) {
        File file = new File(doc.getStorageUrl());
        if (!file.exists()) {
            throw new RuntimeException("文件不存在: " + doc.getStorageUrl());
        }
        Document document = FileSystemDocumentLoader.loadDocument(file.toPath());
        return document.text();
    }

    private String loadFromOss(DocumentEntity doc) {
        if (ossClientManager == null) {
            throw new RuntimeException("OSS客户端管理器未初始化");
        }

        String clientName = aiProperties.getDocument().getOssClientName();
        OssClient ossClient = ossClientManager.get(clientName);

        String objectKey = StrUtil.isNotBlank(doc.getStoragePath()) ? doc.getStoragePath() : doc.getStorageUrl();
        if (StrUtil.isBlank(objectKey)) {
            throw new RuntimeException("OSS对象键为空，无法下载文件");
        }

        log.info("从OSS加载文档: clientName={}, objectKey={}", clientName, objectKey);

        try (S3Object s3Object = ossClient.getObject(objectKey);
                S3ObjectInputStream inputStream = s3Object.getObjectContent()) {

            byte[] bytes = IoUtil.readBytes(inputStream);
            String fileType = doc.getFileType();

            if (isTextFile(fileType)) {
                return new String(bytes, StandardCharsets.UTF_8);
            }

            Document document = loadDocumentFromBytes(bytes, doc.getFileName(), fileType);
            return document.text();

        } catch (Exception e) {
            log.error("从OSS加载文档失败: objectKey={}", objectKey, e);
            throw new RuntimeException("从OSS加载文档失败: " + e.getMessage(), e);
        }
    }

    private boolean isTextFile(String fileType) {
        if (fileType == null) {
            return false;
        }
        String type = fileType.toUpperCase();
        return type.equals("TXT")
                || type.equals("MD")
                || type.equals("JSON")
                || type.equals("XML")
                || type.equals("CSV")
                || type.equals("HTML");
    }

    private Document loadDocumentFromBytes(byte[] bytes, String fileName, String fileType) {
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            DocumentParser parser = createParser(fileType);
            if (parser != null) {
                return parser.parse(inputStream);
            }
            return Document.from(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("文档解析失败，尝试直接读取文本: fileName={}", fileName, e);
            return Document.from(new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private DocumentParser createParser(String fileType) {
        if (fileType == null) {
            return null;
        }
        String type = fileType.toUpperCase();
        return switch (type) {
            case "PDF" -> new ApachePdfBoxDocumentParser();
            case "DOC", "DOCX", "XLS", "XLSX", "PPT", "PPTX" -> new ApachePoiDocumentParser();
            default -> null;
        };
    }

    private void updateStatus(DocumentEntity doc, DocumentStatus status, int progress, String msg) {
        doc.setProcessStatus(status.name());
        doc.setProcessProgress(progress);
        doc.setErrorMessage(msg);
        documentMapper.updateProcessStatus(doc.getId(), status.name(), progress, msg);
    }
}
