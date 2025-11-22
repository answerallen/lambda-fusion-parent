package com.lambda.fusion.authority.role.service.impl;

import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.role.service.InternalRoleService;

import java.util.Collections;
import java.util.Set;

public class InternalRoleServiceImpl implements InternalRoleService {
    @Override
    public Set<String> queryExclude(LoginUser operator) {
        return Collections.emptySet();
    }

    @Override
    public Set<String> deleteExclude(LoginUser operator) {
        return Collections.emptySet();
    }
}
