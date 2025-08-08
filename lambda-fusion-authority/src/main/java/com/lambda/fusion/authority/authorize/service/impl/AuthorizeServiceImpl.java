package com.lambda.fusion.authority.authorize.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.web.TenantHolder;
import com.lambda.fusion.authority.authorize.mapper.AuthorizeMapper;
import com.lambda.fusion.authority.authorize.model.dto.NavigationQueryDTO;
import com.lambda.fusion.authority.authorize.model.vo.SimpleUserVO;
import com.lambda.fusion.authority.authorize.service.AuthorizeService;
import com.lambda.fusion.authority.resource.model.Resource;
import com.lambda.fusion.authority.user.domain.SimpleUser;
import com.lambda.fusion.autoconfig.AuthorityConstants;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthorizeServiceImpl implements AuthorizeService {

    private final AuthorizeMapper authorizeMapper;

    @Override
    public LoginUser loginByUsername(String username, String loginType) {
        Assert.notNull(username, AuthorityConstants.USER_NAME_NOT_EMPTY);
        SimpleUserVO userVO = authorizeMapper.loadUserDetailByUsername(username);
        if (userVO == null) {
            throw new UsernameNotFoundException(AuthorityConstants.USER_NOT_FOUND);
        }
        return buildLoginUser(userVO.toUser());
    }

    @Override
    public LoginUser loginByMobile(String mobile, String loginType) throws AuthenticationException {
        List<SimpleUserVO> userVOList = authorizeMapper.loadUserDetailByMobile(mobile);
        if (CollUtil.isEmpty(userVOList)) {
            throw new UsernameNotFoundException(AuthorityConstants.USER_NOT_FOUND);
        }
        if (userVOList.size() > 1) {
            throw new AuthenticationException(AuthorityConstants.USER_MOBILE_EXIST);
        }
        return buildLoginUser(userVOList.getFirst().toUser());
    }

    @Override
    public List<Resource> getNavigation(LoginUser operator, String parentId, Integer level) {
        NavigationQueryDTO query = new NavigationQueryDTO();
        query.setParentId(parentId);
        query.setLevel(level);
        query.setMode(0);
        return getNavigation(operator, query);
    }

    @Override
    public List<Resource> getNavigation(LoginUser operator, NavigationQueryDTO query) {
        Assert.notNull(operator, "parameter 'operator' cannot be empty or null");
        Assert.notNull(query, "parameter 'query' cannot be empty or null");
        List<Resource> resources = authorizeMapper.getNavigationByQuery(query);
        return TreeFactory.build(resources);
    }

    @Override
    public List<SimpleUser> getUsersByRoleId(String roleId) {
        return authorizeMapper.getUsersByRoleId(roleId);
    }

    @Override
    public LoginUser loadByThirdLoginResult(ThirdPartLoginResult thirdLoginResult, String loginType) {
        return null;
    }

    /**
     * 构建登录用户信息
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
