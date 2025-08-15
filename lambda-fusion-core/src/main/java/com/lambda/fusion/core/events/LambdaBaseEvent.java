package com.lambda.fusion.core.events;

import com.lambda.fusion.core.base.LambdaObject;
import com.lambda.fusion.core.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * @author Jin
 */
@Getter
public abstract class LambdaBaseEvent extends ApplicationEvent {

    private final User operator;

    private transient Object extra;

    protected LambdaBaseEvent(LambdaObject object, User operator) {
        super(object);
        this.operator = operator;
    }

    protected LambdaBaseEvent(LambdaObject object, User operator, Object extra) {
        super(object);
        this.operator = operator;
        this.extra = extra;
    }

    @Override
    public LambdaObject getSource() {
        return (LambdaObject) super.getSource();
    }
}
