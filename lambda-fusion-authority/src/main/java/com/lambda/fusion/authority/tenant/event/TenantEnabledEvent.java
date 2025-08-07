package com.lambda.fusion.authority.tenant.event;

/**
 * 租户启用事件
 *
 */
public class TenantEnabledEvent extends TenantEvent {

    public TenantEnabledEvent(String tenantId) {
        super(tenantId);
    }
}
