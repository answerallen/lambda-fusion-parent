package com.lambda.fusion.authority.role.service;

import com.lambda.cloud.core.principal.LoginUser;
import java.util.Set;

public interface InternalRoleService {

    /**
     * 查询时排除的角色
     *
     * @param operator
     * @return
     */
    Set<String> queryExclude(LoginUser operator);

    /**
     * 不允许删除的角色
     *
     * @param operator
     * @return
     */
    Set<String> deleteExclude(LoginUser operator);
}
