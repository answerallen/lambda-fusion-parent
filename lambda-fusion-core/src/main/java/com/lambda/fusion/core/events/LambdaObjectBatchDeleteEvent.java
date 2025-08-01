package com.lambda.fusion.core.events;

import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.core.base.LambdaObject;
import java.io.Serial;
import java.util.Set;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

/**
 * @author Jin
 */
public class LambdaObjectBatchDeleteEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1L;

    private final LoginUser operator;
    private final Class<? extends LambdaObject> objectClass;

    @Setter
    private transient Object extra;

    public LambdaObjectBatchDeleteEvent(
            Set<String> ids, LoginUser operator, Class<? extends LambdaObject> ObjectClass) {
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

    public Class<? extends LambdaObject> getObjectClass() {
        return objectClass;
    }

    public Object getExtra() {
        return this.extra;
    }
}
