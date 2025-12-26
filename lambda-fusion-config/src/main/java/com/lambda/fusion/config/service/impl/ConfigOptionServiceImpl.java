package com.lambda.fusion.config.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.config.mapper.ConfigOptionMapper;
import com.lambda.fusion.config.model.ConfigOptionEntity;
import com.lambda.fusion.config.service.ConfigOptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统配置选项服务实现类
*/
@Service
@Transactional(rollbackFor = Exception.class)
public class ConfigOptionServiceImpl extends ServiceImpl<ConfigOptionMapper, ConfigOptionEntity>
        implements ConfigOptionService {}
