package com.lambda.fusion.configs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.configs.domain.entity.ConfigOptionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConfigsOptionMapper extends BaseMapper<ConfigOptionEntity> {}
