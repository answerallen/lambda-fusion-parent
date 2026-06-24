package com.lambda.fusion.ai.knowledge.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.ai.knowledge.model.entity.DocumentChunkEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;

/**
 * 文档块分页查询DTO
 *
 * <p>继承Pagination基类，提供统一的分页查询功能，支持按文档ID、向量化状态等条件查询。
 *
 * <h3>功能特性：</h3>
 * <ul>
 * <li>支持文档ID精确查询</li>
 * <li>支持知识库ID精确查询</li>
 * <li>支持向量化状态精确查询</li>
 * <li>支持内容模糊查询</li>
 * <li>参数校验和长度限制</li>
 * </ul>
 *
 */
@Getter
@Setter
@Schema(description = "文档块分页查询参数")
public class DocumentChunkQuery extends PageQuery<DocumentChunkEntity> {

    /**
     * 文档ID
     */
    @Schema(description = "文档ID，用于查询指定文档下的文档块")
    private String documentId;

    /**
     * 知识库ID
     */
    @Schema(description = "知识库ID，用于查询指定知识库下的文档块")
    private String kbId;

    /**
     * 向量化状态
     */
    @Schema(description = "向量化状态，如：PENDING、PROCESSING、COMPLETED、FAILED")
    @Size(max = 50, message = "向量化状态长度不能超过50个字符")
    private String embeddingStatus;

    /**
     * 内容关键词
     */
    @Schema(description = "内容关键词，支持模糊查询")
    @Size(max = 500, message = "内容关键词长度不能超过500个字符")
    private String content;

    /**
     * 页码范围查询
     */
    @Schema(description = "页码，用于查询指定页码的文档块")
    private Integer pageNumber;

    @Override
    public LambdaQueryWrapper<DocumentChunkEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<DocumentChunkEntity> lambdaQueryWrapper = super.getLambdaQueryWrapper();
        lambdaQueryWrapper.eq(documentId != null, DocumentChunkEntity::getDocumentId, documentId);
        lambdaQueryWrapper.eq(kbId != null, DocumentChunkEntity::getKbId, kbId);
        lambdaQueryWrapper.eq(
                StringUtils.isNotBlank(embeddingStatus), DocumentChunkEntity::getEmbeddingStatus, embeddingStatus);
        lambdaQueryWrapper.like(StringUtils.isNotBlank(content), DocumentChunkEntity::getContent, content);
        lambdaQueryWrapper.eq(pageNumber != null, DocumentChunkEntity::getPageNumber, pageNumber);
        return lambdaQueryWrapper;
    }
}
