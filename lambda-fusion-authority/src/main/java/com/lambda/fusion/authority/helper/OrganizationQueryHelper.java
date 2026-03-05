package com.lambda.fusion.authority.helper;

import com.lambda.cloud.core.utils.OperatorUtils;
import com.lambda.fusion.authority.model.organization.OrganizationQuery;
import org.apache.commons.lang.StringUtils;

public class OrganizationQueryHelper {

    /**
     * 获取查询组织机构的参数
     *
     * @return
     */
    public static OrganizationQuery buildOrganizationQuery() {
        OrganizationQuery parameters = new OrganizationQuery();
        String tenantId = OperatorUtils.getOperator().getTenantId();
        parameters.setOwner(StringUtils.isNotBlank(tenantId) ? tenantId : null);
        return parameters;
    }

}
