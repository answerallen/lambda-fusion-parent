package com.lambda.fusion.authority.client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.client.domain.dto.ApiTokenDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * Api Token授权信息 Mapper 接口
 * </p>
 *
 */
@Mapper
public interface ApiTokenMapper extends BaseMapper<ApiTokenDO> {}
