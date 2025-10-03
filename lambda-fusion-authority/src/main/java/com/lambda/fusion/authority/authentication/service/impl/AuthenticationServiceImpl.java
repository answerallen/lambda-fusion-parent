package com.lambda.fusion.authority.authentication.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.web.TenantHolder;
import com.lambda.fusion.authority.authentication.mapper.AuthenticationMapper;
import com.lambda.fusion.authority.authentication.model.NavigationQuery;
import com.lambda.fusion.authority.authentication.model.SimpleUser;
import com.lambda.fusion.authority.authentication.service.AuthenticationService;
import com.lambda.fusion.authority.resource.model.Resource;
import com.lambda.fusion.authority.user.model.vo.SimpleUserVO;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.core.tree.TreeFactory;
import com.lambda.fusion.core.user.User;
import com.lambda.security.exception.AuthenticationException;
import com.lambda.security.exception.UsernameNotFoundException;
import com.lambda.security.provider.ThirdPartLoginResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现类
 * 负责用户认证、授权和导航菜单相关的业务逻辑实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthenticationMapper authenticationMapper;

    @Override
    public LoginUser loginByUsername(String username, String loginType) {
        Assert.notNull(username, "parameter 'username' cannot be empty or null");
        SimpleUser userVO = authenticationMapper.loadUserDetailByUsername(username);
        if (userVO == null) {
            throw new UsernameNotFoundException("user in not found");
        }
        return buildLoginUser(userVO.toUser());
    }

    @Override
    public LoginUser loginByMobile(String mobile, String loginType) throws AuthenticationException {
        List<SimpleUser> simpleUsers = authenticationMapper.loadUserDetailByMobile(mobile);
        if (CollUtil.isEmpty(simpleUsers)) {
            throw new UsernameNotFoundException("mobile in not found");
        }
        if (simpleUsers.size() > 1) {
            throw new AuthenticationException("mobile in not unique");
        }
        SimpleUser simpleUser = simpleUsers.get(0);
        return buildLoginUser(simpleUser.toUser());
    }

    @Override
    public List<Resource> getNavigation(LoginUser operator, String parentId, Integer level) {
        NavigationQuery query = new NavigationQuery();
        query.setParentId(parentId);
        query.setLevel(level);
        query.setMode(0);
        return getNavigation(operator, query);
    }

    @Override
    public List<Resource> getNavigation(LoginUser operator, NavigationQuery query) {
        Assert.notNull(operator, "parameter 'operator' cannot be empty or null");
        Assert.notNull(query, "parameter 'query' cannot be empty or null");
        List<Resource> resources = authenticationMapper.getNavigationByQuery(query);
        return TreeFactory.build(resources);
    }

    @Override
    public List<SimpleUserVO> getUsersByRoleId(String roleId) {
        return authenticationMapper.getUsersByRoleId(roleId);
    }

    @Override
    public LoginUser loadByThirdLoginResult(ThirdPartLoginResult thirdLoginResult, String loginType) {
        // TODO: 实现第三方登录逻辑
        return null;
    }

    /**
     * 构建登录用户信息
     *
     * @param user 用户对象
     * @return 登录用户
     */
    private LoginUser buildLoginUser(User user) {
        if (CollUtil.isEmpty(user.getRoles())) {
            user.setRoles(Sets.newHashSet(Constants.ROLE_USER));
        }
        String tenantId = TenantHolder.getTenantId();
        if (StrUtil.isNotBlank(tenantId)) {
            user.setTenantId(tenantId);
        }
        return user;
    }
}
