package com.lambda.fusion.authority.tenant.event;

/**
 * 租户禁用事件
 *
 */
public class TenantDisabledEvent extends TenantEvent {
    public TenantDisabledEvent(String tenantId) {
        super(tenantId);
    }
}
