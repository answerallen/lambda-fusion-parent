package com.lambda.fusion.ai.runtime.workspace.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.runtime.workspace.entity.WorkspaceAuditEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface WorkspaceAuditMapper extends BaseMapper<WorkspaceAuditEntity> {}
