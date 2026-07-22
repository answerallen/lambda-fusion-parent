package com.lambda.fusion.ai.rag.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lambda.fusion.ai.rag.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识库分页查询参数")
public class KnowledgeBasePage extends PageQuery<KnowledgeBaseEntity> {

    @Schema(description = "知识库名称，支持模糊查询")
    private String name;

    @Schema(description = "是否启用")
    private Boolean enabled;

    @Override
    public LambdaQueryWrapper<KnowledgeBaseEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<KnowledgeBaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(name), KnowledgeBaseEntity::getName, name);
        wrapper.eq(enabled != null, KnowledgeBaseEntity::getEnabled, enabled);
        wrapper.orderByDesc(KnowledgeBaseEntity::getCreatedAt);
        return wrapper;
    }
}
