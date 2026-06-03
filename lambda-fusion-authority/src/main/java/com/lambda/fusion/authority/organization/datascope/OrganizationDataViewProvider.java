package com.lambda.fusion.authority.organization.datascope;

import com.lambda.fusion.authority.organization.model.Organization;
import com.lambda.fusion.authority.organization.model.OrganizationQuery;
import com.lambda.fusion.authority.organization.service.OrganizationService;
import com.lambda.fusion.datascope.commons.provider.DataViewProvider;
import com.lambda.fusion.datascope.model.DataScopeNode;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 组织架构数据视图提供者
 * type = 0 代表组织架构/部门数据
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
@Component
@RequiredArgsConstructor
public class OrganizationDataViewProvider implements DataViewProvider {

    private final OrganizationService organizationService;

    @Override
    public boolean supports(int type) {
        return type == 0;
    }

    @Override
    public List<DataScopeNode> loadDataView(String tenantId) {
        OrganizationQuery query = new OrganizationQuery();
        query.setTenantId(tenantId);
        // 获取所有组织节点（平铺列表）
        List<Organization> organizations = organizationService.selectAll(query);
        Map<String, Boolean> hasChildMap = organizations.stream()
                .filter(item -> item.getParentId() != null)
                .collect(Collectors.toMap(Organization::getParentId, item -> true, (left, right) -> left));
        return organizations.stream()
                .map(organization -> {
                    DataScopeNode node = new DataScopeNode();
                    node.setId(organization.getId());
                    node.setName(organization.getName());
                    node.setPid(organization.getParentId());
                    node.setLevel(organization.getLevel());
                    // 根据是否有子节点判断是否为末级节点
                    boolean isLastStage = !hasChildMap.containsKey(organization.getId());
                    node.setLastStage(isLastStage);
                    // 可以扩展一些额外的属性供前端使用
                    node.getProps().put("alias", organization.getAlias());
                    node.getProps().put("category", organization.getCategory());

                    return node;
                })
                .collect(Collectors.toList());
    }
}
