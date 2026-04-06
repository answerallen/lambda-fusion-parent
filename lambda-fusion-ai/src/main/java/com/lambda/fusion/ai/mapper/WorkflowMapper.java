package com.lambda.fusion.ai.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.WorkflowEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI Agent 工作流配置持久层访问接口
 */
@Mapper
@DS("@aiProperties.dataSource.name")
public interface WorkflowMapper extends BaseMapper<WorkflowEntity> {}
