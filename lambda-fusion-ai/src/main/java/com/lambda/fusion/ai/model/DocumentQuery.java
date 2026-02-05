package com.lambda.fusion.ai.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.ai.model.entity.DocumentEntity;
import com.lambda.fusion.core.pagination.Pagination;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;

/**
 * 文档分页查询DTO
 *
 * <p>继承Pagination基类，提供统一的分页查询功能，支持按知识库ID、处理状态等条件查询。
 *
 * <h3>功能特性：</h3>
 * <ul>
 * <li>支持知识库ID精确查询</li>
 * <li>支持处理状态精确查询</li>
 * <li>支持文件名模糊查询</li>
 * <li>参数校验和长度限制</li>
 * </ul>
 *
 */
@Getter
@Setter
@Schema(description = "文档分页查询参数")
public class DocumentQuery extends Pagination<DocumentEntity> {

    /**
     * 知识库ID
     */
    @Schema(description = "知识库ID，用于查询指定知识库下的文档")
    private Long kbId;

    /**
     * 处理状态
     */
    @Schema(description = "处理状态，如：PENDING、PROCESSING、COMPLETED、FAILED")
    @Size(max = 50, message = "处理状态长度不能超过50个字符")
    private String status;

    /**
     * 文件名
     */
    @Schema(description = "文件名，支持模糊查询")
    @Size(max = 255, message = "文件名长度不能超过255个字符")
    private String fileName;

    @Override
    public LambdaQueryWrapper<DocumentEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<DocumentEntity> lambdaQueryWrapper = super.getLambdaQueryWrapper();
        lambdaQueryWrapper.eq(kbId != null, DocumentEntity::getKbId, kbId);
        lambdaQueryWrapper.eq(StringUtils.isNotBlank(status), DocumentEntity::getProcessStatus, status);
        lambdaQueryWrapper.like(StringUtils.isNotBlank(fileName), DocumentEntity::getFileName, fileName);
        return lambdaQueryWrapper;
    }
}
