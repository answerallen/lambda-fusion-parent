package com.lambda.fusion.authority.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.authority.user.domain.entity.OnlineLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserOnlineLogMapper extends BaseMapper<OnlineLogEntity> {}
