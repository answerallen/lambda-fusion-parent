package com.lambda.fusion.core.events;

import com.lambda.fusion.core.base.LambdaObject;
import com.lambda.fusion.core.user.User;
import org.springframework.context.ApplicationEvent;

/**
 * Lambda基础事件类
 *
 * @author Jin
 */
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

    /**
     * 获取操作用户
     *
     * @return 操作用户对象
     */
    public User getOperator() {
        return operator;
    }

    /**
     * 获取额外数据
     *
     * @return 额外数据对象
     */
    public Object getExtra() {
        return extra;
    }

    @Override
    public LambdaObject getSource() {
        return (LambdaObject) super.getSource();
    }
}
