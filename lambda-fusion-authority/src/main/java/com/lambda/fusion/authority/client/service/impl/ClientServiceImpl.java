package com.lambda.fusion.authority.client.service.impl;

import static com.lambda.fusion.authority.AuthorityConstants.CACHE_MANAGER;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.client.mapper.ClientMapper;
import com.lambda.fusion.authority.client.model.entity.ClientEntity;
import com.lambda.fusion.authority.client.service.ClientService;
import com.lambda.fusion.authority.resource.model.UserPermission;
import com.lambda.security.exception.AuthenticationException;
import com.lambda.security.exception.UsernameNotFoundException;
import com.lambda.security.service.HmacClientService;
import com.lambda.security.web.hmac.model.HmacClient;
import java.util.List;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class ClientServiceImpl extends ServiceImpl<ClientMapper, ClientEntity>
        implements ClientService, HmacClientService {

    @Override
    @CacheEvict(value = "Clients", key = "#entity.id", cacheManager = CACHE_MANAGER)
    public boolean updateById(ClientEntity entity) {
        return super.updateById(entity);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<String> getUsersByResourceId(String rid) {
        return this.baseMapper.getUsersByRid(rid);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Cacheable(value = "Clients", key = "#appid", cacheManager = CACHE_MANAGER)
    public LoginUser loadClientByAppid(String appid) {
        HmacClient client = this.baseMapper.getClientById(appid);
        if (client == null) {
            throw new UsernameNotFoundException("Client " + appid + " not found.");
        }
        return client;
    }

    @Override
    public LoginUser loginByUsername(String username, String loginType) throws AuthenticationException {
        return null;
    }

    @Override
    public List<UserPermission> getUserPermissions(List<String> permissionIds) {
        return this.baseMapper.getUserPermissions(permissionIds);
    }
}
