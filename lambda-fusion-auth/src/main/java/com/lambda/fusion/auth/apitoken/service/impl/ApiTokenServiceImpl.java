package com.lambda.fusion.auth.apitoken.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.auth.apitoken.domain.entity.ApiTokenDO;
import com.lambda.fusion.auth.apitoken.mapper.ApiTokenMapper;
import com.lambda.fusion.auth.apitoken.service.ApiTokenService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * Api Token授权信息 服务实现类
 * </p>
 */
@Service

public class ApiTokenServiceImpl extends ServiceImpl<ApiTokenMapper, ApiTokenDO> implements ApiTokenService {

}
