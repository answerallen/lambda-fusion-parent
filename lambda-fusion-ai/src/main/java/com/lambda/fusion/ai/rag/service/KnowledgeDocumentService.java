package com.lambda.fusion.ai.rag.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.rag.model.KnowledgeDocumentPage;
import com.lambda.fusion.ai.rag.model.entity.KnowledgeDocumentEntity;
import java.io.OutputStream;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeDocumentService {

    Page<KnowledgeDocumentEntity> page(KnowledgeDocumentPage query);

    /**
     * 查询文档详情（校验归属知识库）；不存在抛出业务异常。
     */
    KnowledgeDocumentEntity get(String kbId, String documentId);

    /**
     * 上传文档：落 PENDING 行 + 原文件持久化后异步解析切块入库，立即返回文档行。
     * 原文件持久化失败即上传失败（避免只留下向量数据）。
     *
     * @param kbId 知识库ID
     * @param file 上传文件（pdf/doc/docx/txt/md）
     * @return 文档行（状态 PENDING）
     */
    KnowledgeDocumentEntity upload(String kbId, MultipartFile file);

    /**
     * 下载文档原文件：按文档记录的 storageType 路由到对应存储后端写出内容。
     */
    void download(String kbId, String documentId, OutputStream out);

    /**
     * 删除文档：同步删除向量库中的整文档切块与原文件。
     */
    void delete(String kbId, String documentId);

    /**
     * 级联删除知识库下全部文档（含向量数据与原文件）。供知识库删除调用。
     */
    void deleteByKbId(String kbId);
}
