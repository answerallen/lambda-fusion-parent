package com.lambda.fusion.ai.knowledge.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lambda.fusion.ai.knowledge.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;

/**
 * 知识库分页查询DTO
 *
 * <p>继承Pagination基类，提供统一的分页查询功能，支持按租户ID、状态、名称等条件查询。
 *
 * <h3>功能特性：</h3>
 * <ul>
 * <li>支持租户ID精确查询</li>
 * <li>支持状态精确查询</li>
 * <li>支持知识库名称模糊查询</li>
 * <li>自动处理租户隔离</li>
 * <li>参数校验和长度限制</li>
 * </ul>
 *
 */
@Getter
@Setter
@Schema(description = "知识库分页查询参数")
public class KnowledgeBaseQuery extends PageQuery<KnowledgeBaseEntity> {

    /**
     * 租户ID
     */
    @Schema(description = "租户ID，用于多租户数据隔离")
    private String tenantId;

    /**
     * 状态
     */
    @Schema(description = "知识库状态，如：ACTIVE、INACTIVE、DELETED")
    @Size(max = 50, message = "状态长度不能超过50个字符")
    private String status;

    /**
     * 知识库名称
     */
    @Schema(description = "知识库名称，支持模糊查询")
    @Size(max = 100, message = "知识库名称长度不能超过100个字符")
    private String name;

    /**
     * 分类
     */
    @Schema(description = "知识库分类，支持模糊查询")
    @Size(max = 50, message = "分类长度不能超过50个字符")
    private String category;

    @Override
    public LambdaQueryWrapper<KnowledgeBaseEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<KnowledgeBaseEntity> lambdaQueryWrapper = super.getLambdaQueryWrapper();
        lambdaQueryWrapper.eq(tenantId != null, KnowledgeBaseEntity::getTenantId, tenantId);
        lambdaQueryWrapper.eq(StringUtils.isNotBlank(status), KnowledgeBaseEntity::getStatus, status);
        lambdaQueryWrapper.like(StringUtils.isNotBlank(name), KnowledgeBaseEntity::getName, name);
        lambdaQueryWrapper.like(StringUtils.isNotBlank(category), KnowledgeBaseEntity::getCategory, category);
        return lambdaQueryWrapper;
    }
}
