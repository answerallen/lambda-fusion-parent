package com.lambda.fusion.auth.login.service;

import com.google.common.collect.Maps;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.web.TenantHolder;
import com.lambda.fusion.auth.NavigationParameter;
import com.lambda.fusion.auth.login.domain.UserDTO;
import com.lambda.fusion.auth.login.mapper.AuthorizeMapper;
import com.lambda.fusion.auth.resource.model.Resource;
import com.lambda.fusion.auth.user.domain.SimpleUser;
import com.lambda.fusion.core.tree.TreeFactory;
import com.lambda.security.provider.ThirdPartLoginResult;
import lombok.extern.slf4j.Slf4j;

import java.util.*;


@Slf4j
public class AuthorizeServiceImpl implements AuthorizeService {

    private final AuthorizeMapper authorizeMapper;

    public AuthorizeServiceImpl(AuthorizeMapper authorizeMapper) {
        this.authorizeMapper = authorizeMapper;
    }

    @Override
    public LoginUser loginByUsername(String username, String loginType) {
//        Assert.notNull(username, AuthorizeConstants.USER_NAME_NOT_EMPTY);
        UserDTO userDTO = authorizeMapper.loadUserDetailByUsername(username);
//        if (userDTO == null) {
//            throw new UsernameNotFoundException(AuthorizeConstants.USER_NOT_FOUND);
//        }
        LoginUser source = userDTO.toUser();
//        RoleUtil.mergeDefaultAuthority(source);

        // 从TenantHolder里获取租户ID，tenantHolder的优先级高于数据库中的租户ID
        String tenantId = TenantHolder.getTenantId();
//        if (StringUtils.isNotBlank(tenantId)) {
//            source.setTenantId(tenantId);
//        }

        return source;
    }

    @Override
    public List<Resource> getNavigation(LoginUser operator, String parentId, Integer level) {
        NavigationParameter parameter = new NavigationParameter();
        parameter.setParentId(parentId);
        parameter.setLevel(level);
        parameter.setMode(0);
        return getNavigation(operator, parameter);
    }

    @Override
    public List<Resource> getNavigation(LoginUser operator, NavigationParameter parameter) {
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
//            Map<String, Resource> resourceMap = resources.stream().collect(Collectors.toMap(Resource::getId, r -> r));
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
    public List<SimpleUser> getUsersByRoleId(String roleid) {
        return authorizeMapper.getUsersByRoleId(roleid);
    }

    @Override
    public LoginUser loadByThirdLoginResult(ThirdPartLoginResult thirdLoginResult, String loginType) {
        return null;
    }
}
