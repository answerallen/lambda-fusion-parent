package com.lambda.fusion.authority.tenant.event;

/**
 * 租户配置变更事件
 *
 */
public class TenantConfigurationChangedEvent extends TenantEvent {
    public TenantConfigurationChangedEvent(String tenantId) {
        super(tenantId);
    }
}
