package com.lambda.fusion.ai.runtime.workspace.service;

import com.lambda.fusion.ai.runtime.workspace.entity.WorkspaceAuditEntity;
import java.util.List;

public interface WorkspaceAuditService {

    void record(WorkspaceAuditEntity entity);

    List<WorkspaceAuditEntity> listByAppAndTenant(String appId, String tenantId);
}
