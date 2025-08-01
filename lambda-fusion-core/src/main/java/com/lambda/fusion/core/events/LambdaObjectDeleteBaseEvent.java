package com.lambda.fusion.core.events;

import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.core.base.LambdaObject;

/**
 * @author Jin
 */
public class LambdaObjectDeleteBaseEvent extends LambdaBaseEvent {

    public LambdaObjectDeleteBaseEvent(LambdaObject object, LoginUser operator) {
        super(object, operator);
    }
}
