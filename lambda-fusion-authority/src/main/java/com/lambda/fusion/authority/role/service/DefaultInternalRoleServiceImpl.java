package com.lambda.fusion.authority.role.service;

import com.lambda.cloud.core.principal.LoginUser;
import java.util.Collections;
import java.util.Set;

public class DefaultInternalRoleServiceImpl implements InternalRoleService {
    @Override
    public Set<String> queryExclude(LoginUser operator) {
        return Collections.emptySet();
    }

    @Override
    public Set<String> deleteExclude(LoginUser operator) {
        return Collections.emptySet();
    }
}
