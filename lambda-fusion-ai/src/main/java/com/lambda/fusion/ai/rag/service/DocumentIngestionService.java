package com.lambda.fusion.ai.rag.service;

import com.lambda.fusion.ai.AiConstants.DocumentStatus;
import com.lambda.fusion.ai.rag.mapper.KnowledgeDocumentMapper;
import com.lambda.fusion.ai.rag.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.ai.rag.model.entity.KnowledgeDocumentEntity;
import com.lambda.fusion.ai.rag.runtime.IngestChunk;
import com.lambda.fusion.ai.rag.runtime.SimpleKnowledgeAdapter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.agentscope.core.rag.reader.PDFReader;
import io.agentscope.core.rag.reader.Reader;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.reader.TikaReader;
import io.agentscope.core.rag.reader.WordReader;
import java.io.IOException;
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
 * 文档入库管线：临时文件 → Reader 解析切块 → 向量库写入 → 更新文档行状态。
 *
 * <p>{@code ReaderInput} 不支持 InputStream，上传内容在上传端点已落临时文件；
 * 本类异步执行（{@code AiConfigure} 已 {@code @EnableAsync}），结束后 finally 删除临时文件。
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

    /**
     * 异步执行入库；任何失败只落文档行状态（FAILED + error_msg），不向调用方抛出。
     *
     * @param documentId 文档行ID
     * @param tempFile 上传内容的临时文件（处理完删除）
     */
    @Async
    public void ingest(String documentId, Path tempFile) {
        try {
            KnowledgeDocumentEntity document = knowledgeDocumentMapper.selectById(documentId);
            if (document == null) {
                return;
            }
            KnowledgeBaseEntity kb = knowledgeBaseService.loadById(document.getKbId());
            Reader reader = resolveReader(document.getFileType());
            // var 接收 Reader 产出，避免在防腐层之外 import deprecated 的 Document 类型
            var documents = reader.read(ReaderInput.fromFile(tempFile)).block();
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
            log.warn("知识库文档入库失败: doc={}, error={}", documentId, e.getMessage());
            markFailed(documentId, e);
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("删除知识库上传临时文件失败: {}, {}", tempFile, e.getMessage());
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

    // 按扩展名选 Reader；TikaReader 兜底（上传端点已白名单校验，正常不会走到）
    private static Reader resolveReader(String fileType) {
        return switch (StringUtils.defaultString(fileType)) {
            case "pdf" -> new PDFReader();
            case "doc", "docx" -> new WordReader();
            case "txt", "md" -> new TextReader();
            default -> new TikaReader();
        };
    }
}
