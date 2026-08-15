package com.lambda.fusion.ai.rag.service;

import com.lambda.fusion.ai.AiConstants.DocumentStatus;
import com.lambda.fusion.ai.rag.mapper.KnowledgeDocumentMapper;
import com.lambda.fusion.ai.rag.model.entity.KnowledgeDocumentEntity;
import com.lambda.fusion.ai.rag.runtime.IngestChunk;
import com.lambda.fusion.ai.rag.runtime.SimpleKnowledgeAdapter;
import com.lambda.fusion.ai.rag.storage.DocumentFileStorage;
import com.lambda.fusion.ai.rag.storage.DocumentFileStorageResolver;
import com.lambda.fusion.ai.runtime.document.DocumentTextExtractor;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.rag.reader.Reader;
import io.agentscope.core.rag.reader.ReaderInput;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;

/**
 * 文档入库管线：从已持久化的原文件读取 → Reader 解析切块 → 向量库写入 → 更新文档行状态。
 *
 * <p>原文件在 upload 端点已通过 {@link DocumentFileStorage} 持久化（LOCAL/OSS），本类按
 * document 行记录的 {@code storageType} 路由取回，下载到本方法创建的临时文件后解析，finally
 * 删除--临时文件生命周期完全闭合在 {@link #ingest(String)} 内，不跨同步/异步边界传递。
 *
 * <p>本类异步执行（{@code AiConfigure} 已 {@code @EnableAsync}）；调用方在主事务
 * {@code afterCommit} 阶段触发，保证 document 行已提交可见，避免异步线程 selectById 读不到。
 *
 * <p>Reader 产出的 {@code Document}（deprecated 模型类）在此仅取文本与 chunkId 转为自有
 * {@link IngestChunk}，向量文档的组装由 {@link SimpleKnowledgeAdapter} 防腐层完成。
 *
 * @author Jin
 */
@Slf4j
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class DocumentIngestionService {

    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final SimpleKnowledgeAdapter simpleKnowledgeAdapter;
    private final DocumentFileStorageResolver storageResolver;

    /**
     * 异步执行入库；任何失败只落文档行状态（FAILED + error_msg），不向调用方抛出。
     *
     * <p>从已持久化的原文件取回内容（而非依赖 upload 临时文件路径），临时文件由本方法创建并清理。
     *
     * @param documentId 文档行ID
     */
    @Async
    public void ingest(String documentId) {
        Path tempFile = null;
        try {
            KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectById(documentId);
            if (document == null) {
                return;
            }
            // 按 document 行记录的 storageType 路由（配置变更后老文档仍能命中其原始存储后端）
            DocumentFileStorage storage = storageResolver.resolve(document.getStorageType());
            tempFile = Files.createTempFile("kb-ingest-", "." + StringUtils.defaultString(document.getFileType()));
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                storage.download(document.getStoragePath(), out);
            }

            Reader reader = DocumentTextExtractor.resolveReader(document.getFileType());
            // ReaderInput 构造按 reader 期望区分：二进制走 fromPath、文本自行读（UTF-8 优先 GBK 兜底）
            ReaderInput input = DocumentTextExtractor.buildReaderInput(document.getFileType(), tempFile);
            var documents = reader.read(input).block();
            List<IngestChunk> chunks = new ArrayList<>();
            if (documents != null) {
                int index = 0;
                for (var doc : documents) {
                    String chunkId = doc.getMetadata().getChunkId();
                    chunks.add(new IngestChunk(
                            StringUtils.defaultIfBlank(chunkId, documentId + "-" + index),
                            doc.getMetadata().getContentText()));
                    index++;
                }
            }
            simpleKnowledgeAdapter.addChunks(
                    document.getKbId(), document.getId(), document.getTenantId(), document.getFileName(), chunks);
            document.setStatus(DocumentStatus.READY.getCode());
            document.setChunkCount(chunks.size());
            document.setErrorMsg(null);
            document.setUpdatedAt(LocalDateTime.now());
            knowledgeDocumentMapper.updateById(document);
            log.info("知识库文档入库完成: doc={}, chunks={}", documentId, chunks.size());
        } catch (Exception e) {
            log.error("知识库文档入库失败: doc={}", documentId, e);
            markFailed(documentId, e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("删除知识库入库临时文件失败: {}, {}", tempFile, e.getMessage());
                }
            }
        }
    }

    private void markFailed(String documentId, Exception e) {
        try {
            KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectById(documentId);
            if (document == null) {
                return;
            }
            document.setStatus(DocumentStatus.FAILED.getCode());
            document.setErrorMsg(StringUtils.left(e.getMessage(), 1000));
            document.setUpdatedAt(LocalDateTime.now());
            knowledgeDocumentMapper.updateById(document);
        } catch (Exception updateError) {
            log.warn("更新文档失败状态出错: doc={}, {}", documentId, updateError.getMessage());
        }
    }
}
