package com.lambda.fusion.authority.tenant.event;

import org.springframework.context.ApplicationEvent;

/**
 * 租户事件基类
 *
 */
public class TenantEvent extends ApplicationEvent {

    public TenantEvent(Object source) {
        super(source);
    }
}
