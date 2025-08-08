package com.lambda.fusion.authority.authorize.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.web.TenantHolder;
import com.lambda.fusion.authority.authorize.mapper.AuthorizeMapper;
import com.lambda.fusion.authority.authorize.model.dto.NavigationQueryDTO;
import com.lambda.fusion.authority.authorize.model.vo.SimpleUserVO;
import com.lambda.fusion.authority.authorize.service.AuthorizeService;
import com.lambda.fusion.authority.resource.model.Resource;
import com.lambda.fusion.autoconfig.AuthorityConstants;
import com.lambda.fusion.core.Constants;
import com.lambda.fusion.core.tree.TreeFactory;
import com.lambda.fusion.core.user.User;
import com.lambda.security.exception.AuthenticationException;
import com.lambda.security.exception.UsernameNotFoundException;
import com.lambda.security.provider.ThirdPartLoginResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        SimpleUserVO simpleUserVO = authorizeMapper.loadUserDetailByUsername(username);
        if (simpleUserVO == null) {
            throw new UsernameNotFoundException(AuthorityConstants.USER_NOT_FOUND);
        }
        User source = simpleUserVO.toUser();
        if (CollUtil.isEmpty(source.getRoles())) {
            source.setRoles(Sets.newHashSet(Constants.ROLE_USER));
        }
        String tenantId = TenantHolder.getTenantId();
        if (StrUtil.isNotBlank(tenantId)) {
            source.setTenantId(tenantId);
        }
        return source;
    }

    @Override
    public LoginUser loginByMobile(String mobile, String loginType) throws AuthenticationException {
        List<SimpleUserVO> simpleUserVOS = authorizeMapper.loadUserDetailByMobile(mobile);
        if (CollUtil.isEmpty(simpleUserVOS)) {
            throw new UsernameNotFoundException(AuthorityConstants.USER_NOT_FOUND);
        }
        if (simpleUserVOS.size() > 1) {
            throw new AuthenticationException(AuthorityConstants.USER_MOBILE_EXIST);
        }
        User source = simpleUserVOS.getFirst().toUser();
        if (CollUtil.isEmpty(source.getRoles())) {
            source.setRoles(Sets.newHashSet(Constants.ROLE_USER));
        }
        String tenantId = TenantHolder.getTenantId();
        if (StrUtil.isNotBlank(tenantId)) {
            source.setTenantId(tenantId);
        }
        return source;
    }

    @Override
    public List<Resource> getNavigation(LoginUser operator, String parentId, Integer level) {
        NavigationQueryDTO parameter = new NavigationQueryDTO();
        parameter.setParentId(parentId);
        parameter.setLevel(level);
        parameter.setMode(0);
        return getNavigation(operator, parameter);
    }

    @Override
    public List<Resource> getNavigation(LoginUser operator, NavigationQueryDTO parameter) {
        Assert.notNull(operator, "parameter 'operator' cannot be empty or null");
        //        boolean dev = OperatorUtils.isDev(operator);
        Map<String, Object> parameters = Maps.newHashMapWithExpectedSize(4);
        parameters.put("parentId", parameter.getParentId());
        parameters.put("level", parameter.getLevel());
        parameters.put("mode", Optional.of(parameter.getMode()).orElse(0));
        //        if (!dev) {
        //            Set<SimpleGrantedAuthority> authrs = operator.getAuthorities();
        //            String uid = operator.getUsername();
        //            Set<String> ids = new HashSet<>(authrs.size() + 1);
        //            ids.add(uid);
        //            ids.addAll(authrs.stream().map(SimpleGrantedAuthority::getAuthority).collect(Collectors.toSet()));
        //            parameters.put("ids", ids);
        //        }
        // 用户拥有的权限
        List<Resource> resources = authorizeMapper.getNavigationByParams(parameters);
        //        NavigationUtils.migrateButtons(resources);

        //        if (parameter.getAll() == 1 && !OperatorUtils.isDev(operator)) {
        //            // 查询所有, 没有权限的只包含简单信息
        //            Map<String, Resource> resourceMap = resources.stream().collect(Collectors.toMap(Resource::getId, r
        // -> r));
        //            Map<String, Object> parameters2 = Maps.newHashMapWithExpectedSize(1);
        //            parameters2.put("mode", Optional.of(parameter.getMode()).orElse(0));
        //            parameters2.put("types", new Integer[]{1,2,4});
        //            List<Resource> all = laAuthorizeMapper.getAllResourcesSimple(parameters2);
        //            all.forEach(r -> {
        //                if (resourceMap.containsKey(r.getId())) {
        //                    BeanUtils.copyProperties2(resourceMap.get(r.getId()), r);
        //                }else{
        //                    r.setChecked(false);
        //                }
        //            });
        //            resources = all;
        //        }

        return TreeFactory.build(resources);
    }

    @Override
    public List<com.lambda.fusion.authority.user.domain.SimpleUser> getUsersByRoleId(String roleid) {
        return authorizeMapper.getUsersByRoleId(roleid);
    }

    @Override
    public LoginUser loadByThirdLoginResult(ThirdPartLoginResult thirdLoginResult, String loginType) {
        return null;
    }
}
