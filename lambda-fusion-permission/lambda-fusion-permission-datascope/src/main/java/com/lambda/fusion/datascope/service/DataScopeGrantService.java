package com.lambda.fusion.datascope.service;

import com.lambda.fusion.datascope.model.GrantDataScope;
import com.lambda.fusion.datascope.model.DataScopeNode;

import java.util.List;

public interface DataScopeGrantService {

    /**
     * 获取数据权限分配树（含选中状态）
     */
    List<DataScopeNode> getDataScopeTree(int type, String targetId, String targetType);

    /**
     * 授权数据权限
     */
    void grantDataScope(GrantDataScope req);
}
