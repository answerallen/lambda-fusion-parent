package com.lambda.fusion.authority.client.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.client.mapper.ClientMapper;
import com.lambda.fusion.authority.client.model.dto.ClientPageQueryDTO;
import com.lambda.fusion.authority.client.model.entity.ClientEntity;
import com.lambda.fusion.authority.client.service.ClientService;
import com.lambda.fusion.authority.resource.model.UserPermission;
import com.lambda.security.exception.AuthenticationException;
import com.lambda.security.exception.UsernameNotFoundException;
import com.lambda.security.service.HmacClientService;
import com.lambda.security.web.hmac.model.HmacClient;
import org.apache.commons.lang.StringUtils;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

import static com.lambda.fusion.authority.AuthorityConstants.CACHE_MANAGER;

@Service
@Transactional(rollbackFor = Exception.class)
public class ClientServiceImpl extends ServiceImpl<ClientMapper, ClientEntity>
        implements ClientService, HmacClientService {

    @Override
    public boolean save(ClientEntity entity) {
        Date now = new Date();
        entity.setSecret(UUID.fastUUID().toString());
        entity.setCreated(now);
        entity.setUpdated(now);
        return super.save(entity);
    }

    @Override
    @CacheEvict(value = "Clients", key = "#entity.id", cacheManager = CACHE_MANAGER)
    public boolean updateById(ClientEntity entity) {
        Date now = new Date();
        entity.setUpdated(now);
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
