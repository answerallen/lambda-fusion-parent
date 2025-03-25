package com.lamuda.cloud.scaffold.core.events;


import com.lamuda.cloud.core.principal.LoginUser;
import com.lamuda.cloud.scaffold.core.base.FxObject;
import org.springframework.context.ApplicationEvent;

import java.util.Set;

/**
 * @author Jin
 */
public class FxObjectBatchDeleteEvent extends ApplicationEvent {

    private final LoginUser operator;
    private final Class<? extends FxObject> objectClass;
    private transient Object extra;

    public FxObjectBatchDeleteEvent(Set<String> ids, LoginUser operator, Class<? extends FxObject> ObjectClass) {
        super(ids);
        this.operator = operator;
        this.objectClass = ObjectClass;
    }

    public LoginUser getOperator() {
        return operator;
    }


    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getSource() {
        return (Set<String>) super.getSource();
    }

    public Class<? extends FxObject> getObjectClass() {
        return objectClass;
    }

    public Object getExtra() {
        return this.extra;
    }

    public void setExtra(Object extra) {
        this.extra = extra;
    }
}
