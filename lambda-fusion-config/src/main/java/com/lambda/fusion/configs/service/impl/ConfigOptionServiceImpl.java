package com.lambda.fusion.configs.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.configs.domain.entity.ConfigOptionEntity;
import com.lambda.fusion.configs.mapper.ConfigsOptionMapper;
import com.lambda.fusion.configs.service.ConfigOptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class ConfigOptionServiceImpl extends ServiceImpl<ConfigsOptionMapper, ConfigOptionEntity>
        implements ConfigOptionService {}
