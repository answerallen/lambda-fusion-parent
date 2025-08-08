package com.lambda.fusion.authority.role.service;

import com.google.common.collect.Sets;
import com.lambda.fusion.authority.role.mapper.RoleMapper;
import com.lambda.fusion.authority.role.model.vo.UserAuthorityVO;
import com.lambda.fusion.core.Constants;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

@Service
public class RoleManagerImpl implements RoleManager {
    private final RoleMapper roleMapper;

    public RoleManagerImpl(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Nonnull
    @Override
    public Set<String> getAuthoritiesByUser(String uid) {
        List<UserAuthorityVO> results = roleMapper.getAuthoritiesByUser(uid);
        if (CollectionUtils.isEmpty(results)) {
            return Sets.newHashSet();
        }
        Set<String> authorities = Sets.newHashSetWithExpectedSize(results.size());
        results.forEach(item -> {
            String authority = item.getAuthority();
            String orgid = item.getOrgid();
            if (Constants.ROLE_TENANT.equals(item.getAuthority())) {
                authority = Constants.ROLE_TENANT + Constants.AT + orgid;
            }
            authorities.add(authority);
        });
        return authorities;
    }
}
