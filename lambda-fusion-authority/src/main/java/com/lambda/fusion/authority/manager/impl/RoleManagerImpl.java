package com.lambda.fusion.authority.manager.impl;

import com.google.common.collect.Sets;
import com.lambda.fusion.authority.manager.RoleManager;
import com.lambda.fusion.authority.mapper.RoleMapper;
import com.lambda.fusion.authority.model.role.UserAuthority;
import com.lambda.fusion.core.FusionConstants;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

@Service
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class RoleManagerImpl implements RoleManager {
    private final RoleMapper roleMapper;

    public RoleManagerImpl(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Nonnull
    @Override
    public Set<String> getAuthoritiesByUser(String uid) {
        List<UserAuthority> results = roleMapper.getAuthoritiesByUser(uid);
        if (CollectionUtils.isEmpty(results)) {
            return Sets.newHashSet();
        }
        Set<String> authorities = Sets.newHashSetWithExpectedSize(results.size());
        results.forEach(item -> {
            String authority = item.getAuthority();
            String orgId = item.getOrgId();
            if (FusionConstants.ROLE_TENANT.equals(item.getAuthority())) {
                authority = FusionConstants.ROLE_TENANT + FusionConstants.AT + orgId;
            }
            authorities.add(authority);
        });
        return authorities;
    }
}
