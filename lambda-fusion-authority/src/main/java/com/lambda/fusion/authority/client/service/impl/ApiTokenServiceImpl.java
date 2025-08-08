package com.lambda.fusion.authority.client.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.authority.client.mapper.ApiTokenMapper;
import com.lambda.fusion.authority.client.model.entity.ApiTokenEntity;
import com.lambda.fusion.authority.client.service.ApiTokenService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * Api Token授权信息 服务实现类
 * </p>
 */
@Service
public class ApiTokenServiceImpl extends ServiceImpl<ApiTokenMapper, ApiTokenEntity> implements ApiTokenService {}
