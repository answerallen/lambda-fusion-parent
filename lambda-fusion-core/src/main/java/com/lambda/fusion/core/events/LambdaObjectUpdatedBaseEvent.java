package com.lambda.fusion.core.events;

import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.core.base.LambdaObject;

/**
 * @author Jin
 */
public class LambdaObjectUpdatedBaseEvent extends LambdaBaseEvent {

    public LambdaObjectUpdatedBaseEvent(LambdaObject object, LoginUser operator) {
        super(object, operator);
    }
}
