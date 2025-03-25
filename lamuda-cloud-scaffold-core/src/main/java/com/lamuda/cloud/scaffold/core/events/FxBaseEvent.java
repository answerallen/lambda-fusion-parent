package com.lamuda.cloud.scaffold.core.events;


import com.lamuda.cloud.core.principal.LoginUser;
import com.lamuda.cloud.scaffold.core.base.FxObject;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * @author Jin
 */
@Getter
public abstract class FxBaseEvent extends ApplicationEvent {

    private final LoginUser operator;

    private transient Object extra;

    protected FxBaseEvent(FxObject object, LoginUser operator) {
        super(object);
        this.operator = operator;
    }

    protected FxBaseEvent(FxObject object, LoginUser operator, Object extra) {
        super(object);
        this.operator = operator;
        this.extra = extra;
    }


    @Override
    public FxObject getSource() {
        return (FxObject) super.getSource();
    }

}
