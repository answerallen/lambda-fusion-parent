package com.lambda.fusion.auth.role.service;

import com.google.common.collect.Sets;

import com.lambda.fusion.auth.role.bean.UserAuthority;
import com.lambda.fusion.auth.role.persistence.RoleMapper;
import com.lambda.fusion.core.Constants;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;


@Service
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
            String orgid = item.getOrgid();
            if (Constants.ROLE_TENANT.equals(item.getAuthority())) {
                authority = Constants.ROLE_TENANT + Constants.AT + orgid;
            }
            authorities.add(authority);
        });
        return authorities;
    }
}
