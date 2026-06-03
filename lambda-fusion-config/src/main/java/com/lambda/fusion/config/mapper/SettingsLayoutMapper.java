package com.lambda.fusion.config.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.config.model.entity.SettingsLayoutEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@InterceptorIgnore(tenantLine = "true")
public interface SettingsLayoutMapper extends BaseMapper<SettingsLayoutEntity> {}
