package com.lambda.fusion.authority.tenant.event;

/**
 * 租户审核拒绝事件
 *
 */
public class TenantRejectedEvent extends TenantEvent {

    public TenantRejectedEvent(String tenantId) {
        super(tenantId);
    }
}
