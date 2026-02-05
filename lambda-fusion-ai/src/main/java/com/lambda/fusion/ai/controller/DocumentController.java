package com.lambda.fusion.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.model.Document;
import com.lambda.fusion.ai.model.DocumentChunk;
import com.lambda.fusion.ai.model.DocumentChunkQuery;
import com.lambda.fusion.ai.model.DocumentQuery;
import com.lambda.fusion.ai.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档管理Controller
 *
 * @author Jin
 */
@RestController
@RequestMapping("/v1/knowledge-bases/{kbId}/documents")
@Tag(name = "文档管理", description = "文档相关接口")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    @Operation(summary = "上传文档", description = "上传文档到指定知识库")
    public Document upload(
            @Parameter(description = "知识库ID", required = true) @PathVariable Long kbId,
            @Parameter(description = "文件", required = true) @RequestParam("file") MultipartFile file,
            @Parameter(description = "上传用户ID", required = true) @RequestParam Long uploadedBy) {
        return documentService.uploadDocument(kbId, file, uploadedBy);
    }

    @GetMapping({"/page", "/page/{number:\\d+}", "/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(summary = "分页查询文档", description = "分页查询知识库中的文档列表")
    public IPage<Document> page(
            @Parameter(description = "知识库ID", required = true) @PathVariable Long kbId,
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @Valid DocumentQuery documentQuery) {
        if (number != null) {
            documentQuery.setPageNum(number);
        }
        if (size != null) {
            documentQuery.setPageSize(size);
        }
        documentQuery.setKbId(kbId);
        return documentService.pageDocuments(documentQuery);
    }

    @GetMapping
    @Operation(summary = "查询文档列表", description = "查询知识库中的所有文档")
    public List<Document> list(
            @Parameter(description = "知识库ID", required = true) @PathVariable Long kbId,
            @Parameter(description = "处理状态") @RequestParam(required = false) String status) {
        return documentService.listByKbId(kbId, status);
    }

    @GetMapping("/{docId}")
    @Operation(summary = "查询文档详情", description = "根据ID查询文档详细信息")
    public Document getById(
            @Parameter(description = "知识库ID") @PathVariable Long kbId,
            @Parameter(description = "文档ID", required = true) @PathVariable Long docId) {
        return documentService.getDocumentById(docId);
    }

    @DeleteMapping("/{docId}")
    @Operation(summary = "删除文档", description = "删除指定文档(含物理文件)")
    public void delete(
            @Parameter(description = "知识库ID") @PathVariable Long kbId,
            @Parameter(description = "文档ID", required = true) @PathVariable Long docId) {
        documentService.deleteDocument(docId);
    }

    @GetMapping("/{docId}/status")
    @Operation(summary = "查询处理状态", description = "查询文档处理状态和进度")
    public String getStatus(
            @Parameter(description = "知识库ID") @PathVariable Long kbId,
            @Parameter(description = "文档ID", required = true) @PathVariable Long docId) {
        return documentService.getProcessStatus(docId);
    }

    @GetMapping({"/{docId}/chunks/page", "/{docId}/chunks/page/{number:\\d+}", "/{docId}/chunks/page/{number:\\d+}/size/{size:\\d+}"})
    @Operation(summary = "分页查询文档块", description = "查看文档切分后的块列表")
    public IPage<DocumentChunk> pageChunks(
            @Parameter(description = "知识库ID") @PathVariable Long kbId,
            @Parameter(description = "文档ID", required = true) @PathVariable Long docId,
            @PathVariable(required = false) Integer number,
            @PathVariable(required = false) Integer size,
            @Valid DocumentChunkQuery documentChunkQuery) {
        if (number != null) {
            documentChunkQuery.setPageNum(number);
        }
        if (size != null) {
            documentChunkQuery.setPageSize(size);
        }
        documentChunkQuery.setDocumentId(docId);
        documentChunkQuery.setKbId(kbId);
        return documentService.pageChunks(documentChunkQuery);
    }

    @PostMapping("/{docId}/reprocess")
    @Operation(summary = "重新处理文档", description = "重新执行文档切分和向量化")
    public void reprocess(
            @Parameter(description = "知识库ID") @PathVariable Long kbId,
            @Parameter(description = "文档ID", required = true) @PathVariable Long docId) {
        documentService.reprocessDocument(kbId,docId);
    }
}
