package com.lambda.fusion.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.workflow.model.entity.WorkflowEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI Agent 工作流配置持久层访问接口
 */
@Mapper
public interface WorkflowMapper extends BaseMapper<WorkflowEntity> {}
