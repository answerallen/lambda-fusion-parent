package com.lambda.fusion.ai.rag.model;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lambda.fusion.ai.rag.model.entity.KnowledgeDocumentEntity;
import com.lambda.fusion.core.pagination.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识库文档分页查询参数")
public class KnowledgeDocumentPage extends PageQuery<KnowledgeDocumentEntity> {

    @Schema(description = "所属知识库ID")
    private String kbId;

    @Schema(description = "文件名，支持模糊查询")
    private String fileName;

    @Schema(description = "入库状态: PENDING/READY/FAILED")
    private String status;

    @Override
    public LambdaQueryWrapper<KnowledgeDocumentEntity> getLambdaQueryWrapper() {
        LambdaQueryWrapper<KnowledgeDocumentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(kbId), KnowledgeDocumentEntity::getKbId, kbId);
        wrapper.like(StringUtils.isNotBlank(fileName), KnowledgeDocumentEntity::getFileName, fileName);
        wrapper.eq(StringUtils.isNotBlank(status), KnowledgeDocumentEntity::getStatus, status);
        wrapper.orderByDesc(KnowledgeDocumentEntity::getCreatedAt);
        return wrapper;
    }
}
