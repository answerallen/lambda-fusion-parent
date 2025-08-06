package com.lambda.fusion.auth.apitoken.mapper;

import com.lambda.fusion.auth.apitoken.domain.entity.ApiTokenDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * Api Token授权信息 Mapper 接口
 * </p>
 *
 */
@Mapper
public interface ApiTokenMapper extends BaseMapper<ApiTokenDO> {

}
