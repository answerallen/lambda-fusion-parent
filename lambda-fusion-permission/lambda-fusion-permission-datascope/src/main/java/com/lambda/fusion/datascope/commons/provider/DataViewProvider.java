package com.lambda.fusion.datascope.commons.provider;

import com.lambda.fusion.datascope.model.PurviewNode;

import java.util.List;

/**
 * 业务数据视图提供者策略接口
 */
public interface DataViewProvider {

    /**
     * 是否支持处理该类型的数据
     *
     * @param type 业务数据类型
     * @return boolean
     */
    boolean supports(int type);

    /**
     * 加载完整的业务数据视图树形结构
     *
     * @param tenantId 租户ID
     * @return 节点列表(扁平列表，由业务层统一组装树)
     */
    List<PurviewNode> loadDataView(String tenantId);
}
