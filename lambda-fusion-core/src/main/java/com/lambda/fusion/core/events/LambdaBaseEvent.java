package com.lambda.fusion.core.events;

import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.core.base.LambdaObject;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * @author Jin
 */
@Getter
public abstract class LambdaBaseEvent extends ApplicationEvent {

    private final LoginUser operator;

    private transient Object extra;

    protected LambdaBaseEvent(LambdaObject object, LoginUser operator) {
        super(object);
        this.operator = operator;
    }

    protected LambdaBaseEvent(LambdaObject object, LoginUser operator, Object extra) {
        super(object);
        this.operator = operator;
        this.extra = extra;
    }

    @Override
    public LambdaObject getSource() {
        return (LambdaObject) super.getSource();
    }
}
