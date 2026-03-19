package com.lambda.fusion.authority.manager;

import com.google.common.collect.Sets;
import com.lambda.fusion.authority.mapper.RoleMapper;
import com.lambda.fusion.authority.model.role.UserAuthority;
import com.lambda.fusion.core.FusionConstants;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

/**
 * 角色基础操作
 *
 */
@Service
@RequiredArgsConstructor
@SuppressFBWarnings("EI_EXPOSE_REP2")
public class RoleManager {
    private final RoleMapper roleMapper;

    @Nonnull
    public Set<String> getAuthoritiesByUser(String username) {
        List<UserAuthority> results = roleMapper.getAuthoritiesByUser(username);
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
