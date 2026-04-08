package com.lambda.fusion.ai.model;

import com.lambda.cloud.core.annotation.AutoConverter;
import com.lambda.cloud.core.shared.BaseDTO;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.Data;

/**
 * 创建知识库DTO
 *
 * @author Jin
 */
@AutoConverter(target = KnowledgeBaseEntity.class)
@Data
@Schema(description = "创建知识库DTO")
public class CreateKnowledgeBase extends BaseDTO<KnowledgeBaseEntity> {

    @NotBlank(message = "知识库名称不能为空")
    @Size(min = 1, max = 100, message = "知识库名称长度必须在1-100字符之间")
    @Schema(description = "知识库名称")
    private String name;

    @Size(max = 500, message = "知识库描述长度不能超过500字符")
    @Schema(description = "知识库描述")
    private String description;

    @Size(max = 50, message = "分类长度不能超过50字符")
    @Schema(description = "分类(技术文档、FAQ、产品手册等)")
    private String category;

    @NotBlank(message = "Embedding模型不能为空")
    @Size(min = 1, max = 100, message = "Embedding模型名称长度必须在1-100字符之间")
    @Schema(description = "Embedding模型", example = "text-embedding-ada-002")
    private String embeddingModel;

    @NotNull(message = "向量维度不能为空")
    @Min(value = 64, message = "向量维度最小为64")
    @Max(value = 4096, message = "向量维度最大为4096")
    @Schema(description = "向量维度", example = "1536")
    private Integer embeddingDimension;

    @Min(value = 100, message = "文本块大小最小为100")
    @Max(value = 4000, message = "文本块大小最大为4000")
    @Schema(description = "文本块大小", example = "500")
    private Integer chunkSize = 500;

    @Min(value = 0, message = "块重叠大小最小为0")
    @Max(value = 500, message = "块重叠大小最大为500")
    @Schema(description = "块重叠大小", example = "50")
    private Integer chunkOverlap = 50;

    @Size(min = 1, max = 50, message = "分段策略长度必须在1-50字符之间")
    @Schema(description = "分段策略", example = "FIXED")
    private String chunkStrategy = "FIXED";

    @Min(value = 1, message = "检索Top-K数量最小为1")
    @Max(value = 100, message = "检索Top-K数量最大为100")
    @Schema(description = "检索Top-K数量", example = "5")
    private Integer retrievalTopK = 5;

    @DecimalMin(value = "0.0", inclusive = false, message = "相似度阈值必须大于0")
    @DecimalMax(value = "1.0", inclusive = true, message = "相似度阈值必须小于等于1")
    @Schema(description = "相似度阈值", example = "0.7")
    private BigDecimal similarityThreshold = BigDecimal.valueOf(0.7);
}
