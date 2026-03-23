package com.lambda.fusion.authority.commons.datascope;

import com.lambda.fusion.authority.model.organization.Organization;
import com.lambda.fusion.authority.model.organization.OrganizationQuery;
import com.lambda.fusion.authority.service.OrganizationService;
import com.lambda.fusion.datascope.model.PurviewNode;
import com.lambda.fusion.datascope.commons.provider.DataViewProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 组织架构数据视图提供者
 * type = 0 代表组织架构/部门数据
 */
@Component
@RequiredArgsConstructor
public class OrgDataViewProvider implements DataViewProvider {

    private final OrganizationService organizationService;

    @Override
    public boolean supports(int type) {
        return type == 0;
    }

    @Override
    public List<PurviewNode> loadDataView(String tenantId) {
        OrganizationQuery query = new OrganizationQuery();
        query.setTenantId(tenantId);
        // 获取所有组织节点（平铺列表）
        List<Organization> organizations = organizationService.selectAll(query);

        return organizations.stream().map(org -> {
            PurviewNode node = new PurviewNode();
            node.setId(org.getId());
            node.setName(org.getName());
            node.setPid(org.getParentId());
            node.setLevel(org.getLevel());
            // 根据是否有子节点判断是否为末级节点
            boolean isLastStage = organizations.stream()
                    .noneMatch(child -> org.getId().equals(child.getParentId()));
            node.setLastStage(isLastStage);
            
            // 可以扩展一些额外的属性供前端使用
            node.getProps().put("alias", org.getAlias());
            node.getProps().put("category", org.getCategory());
            
            return node;
        }).collect(Collectors.toList());
    }
}
