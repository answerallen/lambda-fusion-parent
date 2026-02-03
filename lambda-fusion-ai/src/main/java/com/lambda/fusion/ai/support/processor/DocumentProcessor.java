package com.lambda.fusion.ai.support.processor;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.lambda.fusion.autoconfig.AiProperties;
import com.lambda.fusion.ai.model.entity.DocumentChunkEntity;
import com.lambda.fusion.ai.model.entity.DocumentEntity;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.support.enums.DocumentStatus;
import com.lambda.fusion.ai.mapper.DocumentChunkMapper;
import com.lambda.fusion.ai.mapper.DocumentMapper;
import com.lambda.fusion.ai.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.repository.VectorRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 文档处理管道
 * 负责文档的加载、切分、向量化和存储
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
    private final VectorRepository vectorRepository;
    private final EmbeddingModel embeddingModel;
    private final AiProperties aiProperties;

    @Async
    @Transactional(rollbackFor = Exception.class)
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
            String vectorTableName = kb.getVectorTableName();

            // 4. 文档切分
            int chunkSize = kb.getChunkSize() != null ? kb.getChunkSize() : 500;
            int chunkOverlap = kb.getChunkOverlap() != null ? kb.getChunkOverlap() : 50;

            DocumentSplitter splitter;
            // 简单实现，根据策略选择(目前主要支持递归)
            // String chunkStrategy = kb.getChunkStrategy();
            splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);

            List<TextSegment> segments = splitter.split(new Document(content));
            updateStatus(doc, DocumentStatus.PROCESSING, 40, "文档切分完成: " + segments.size() + "块");

            // 5. 批量生成向量并存储
            int totalChunks = segments.size();
            List<DocumentChunkEntity> chunkEntities = new ArrayList<>();

            for (int i = 0; i < totalChunks; i++) {
                TextSegment segment = segments.get(i);

                // 生成向量
                Response<Embedding> embeddingResponse = embeddingModel.embed(segment);
                List<Double> vector = embeddingResponse.content().vectorAsList().stream()
                        .map(Float::doubleValue)
                        .collect(Collectors.toList());
                // 注意：LangChain4j返回Float列表，转为Double

                // 构建Chunk实体
                DocumentChunkEntity chunk = new DocumentChunkEntity();
                chunk.setChunkId(IdUtil.fastSimpleUUID());
                chunk.setDocumentId(doc.getId()); // 使用数据库ID
                chunk.setKbId(kb.getId());
                chunk.setContent(segment.text());
                chunk.setChunkIndex(i);
                chunk.setEmbeddingStatus("COMPLETED");
                chunk.setVectorId(IdUtil.fastSimpleUUID()); // 向量ID

                // 保存Chunk到数据库
                documentChunkMapper.insert(chunk);
                chunkEntities.add(chunk);

                // 保存向量到向量表
                vectorRepository.insertVector(
                        vectorTableName,
                        chunk.getId(), // 使用Chunk的主键ID作为关联
                        chunk.getVectorId(),
                        segment.text(),
                        JSONUtil.toJsonStr(segment.metadata()),
                        vector);

                // 更新进度
                if (i % 10 == 0) {
                    int progress = 40 + (int) ((double) i / totalChunks * 50);
                    updateStatus(doc, DocumentStatus.PROCESSING, progress, "向量化中...");
                }
            }

            // 6. 更新文档统计信息和最终状态
            doc.setChunkCount(totalChunks);
            doc.setVectorCount(totalChunks);
            doc.setProcessStatus(DocumentStatus.COMPLETED.name());
            doc.setProcessProgress(100);
            documentMapper.updateById(doc);

            log.info("文档处理完成: {}", documentId);

        } catch (Exception e) {
            log.error("文档处理失败: " + e.getMessage(), e);
            updateStatus(doc, DocumentStatus.FAILED, 0, e.getMessage());
        }
    }

    private String loadContent(DocumentEntity doc) {
        // 目前仅支持本地LOCAL存储的TXT/Markdown
        // TODO: 支持OSS和更多文件格式(PDF/Word)
        if ("LOCAL".equals(doc.getStorageType())) {
            File file = new File(doc.getStoragePath());
            if (!file.exists()) {
                throw new RuntimeException("文件不存在: " + doc.getStoragePath());
            }
            return FileUtil.readString(file, StandardCharsets.UTF_8);
        }
        throw new UnsupportedOperationException("不支持的存储类型: " + doc.getStorageType());
    }

    private void updateStatus(DocumentEntity doc, DocumentStatus status, int progress, String msg) {
        doc.setProcessStatus(status.name());
        doc.setProcessProgress(progress);
        doc.setErrorMessage(msg);
        //        documentMapper.updateProcessStatus(doc); // 使用自定义的更新方法
    }
}
