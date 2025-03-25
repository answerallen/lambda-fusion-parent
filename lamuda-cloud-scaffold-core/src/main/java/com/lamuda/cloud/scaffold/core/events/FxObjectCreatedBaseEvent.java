package com.lamuda.cloud.scaffold.core.events;


import com.lamuda.cloud.core.principal.LoginUser;
import com.lamuda.cloud.scaffold.core.base.FxObject;

/**
 * @author Jin
 */
public class FxObjectCreatedBaseEvent extends FxBaseEvent {

    public FxObjectCreatedBaseEvent(FxObject object, LoginUser operator) {
        super(object, operator);
    }
}
