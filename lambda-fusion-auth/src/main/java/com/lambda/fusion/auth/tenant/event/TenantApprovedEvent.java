package com.lambda.fusion.auth.tenant.event;

/**
 * 租户审核通过事件
 */
public class TenantApprovedEvent extends TenantEvent {

    public TenantApprovedEvent(String tenantId) {
        super(tenantId);
    }
}
