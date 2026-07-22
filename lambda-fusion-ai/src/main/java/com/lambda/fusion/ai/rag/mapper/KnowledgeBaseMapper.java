package com.lambda.fusion.ai.rag.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.rag.model.entity.KnowledgeBaseEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseEntity> {}
