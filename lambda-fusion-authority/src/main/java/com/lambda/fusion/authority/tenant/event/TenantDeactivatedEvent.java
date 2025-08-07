package com.lambda.fusion.authority.tenant.event;

/**
 * 租户停用用事件
 *
 */
public class TenantDeactivatedEvent extends TenantEvent {
    public TenantDeactivatedEvent(String tenantId) {
        super(tenantId);
    }
}
