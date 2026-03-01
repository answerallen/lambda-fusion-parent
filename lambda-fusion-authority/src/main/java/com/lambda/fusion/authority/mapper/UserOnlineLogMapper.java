package com.lambda.fusion.authority.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.domain.user.OnlineLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserOnlineLogMapper extends BaseMapper<OnlineLogEntity> {}
