package com.lambda.fusion.configs.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.logger.context.LogContext;
import com.lambda.fusion.configs.domain.dto.Parameters;
import com.lambda.fusion.configs.domain.entity.ConfigEntity;
import com.lambda.fusion.configs.domain.entity.ConfigOptionEntity;
import com.lambda.fusion.configs.domain.vo.ConfigOptionVO;
import com.lambda.fusion.configs.domain.vo.ConfigVO;
import com.lambda.fusion.configs.mapper.ConfigsMapper;
import com.lambda.fusion.configs.mapper.ConfigsOptionMapper;
import com.lambda.fusion.configs.service.ConfigService;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import static com.lambda.fusion.core.utils.ParameterUtils.fuzzyQuery;

@Service
@Transactional(rollbackFor = Exception.class)
public class ConfigServiceImpl extends ServiceImpl<ConfigsMapper, ConfigEntity> implements ConfigService {

    @Autowired
    private ConfigsMapper configsMapper;

    @Autowired
    private ConfigsOptionMapper configsOptionMapper;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public Page<ConfigEntity> page(Page<ConfigEntity> page, Parameters parameters) {
        if (StringUtils.isNotBlank(parameters.getName())) {
            parameters.setName(fuzzyQuery(parameters.getName()));
        }
        return configsMapper.selectConfigPage(page, parameters);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public List<ConfigEntity> queryConfigsByConditions(String application, Collection<String> ids) {
        return configsMapper.selectAllSystemConfigs(application, ids);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public ConfigEntity getById(Serializable id) {
        return configsMapper.selectConfigById((String) id);
    }

    @Override
    public void updateBatchByApplication(String application, List<ConfigEntity> updated) {
        if (CollectionUtils.isNotEmpty(updated)) {
            List<ConfigEntity> updated0 = Lists.newArrayList();
            for (ConfigEntity entity : updated) {
                ConfigEntity entity0 = new ConfigEntity();
                entity0.setId(entity.getId());
                entity0.setValue(entity.getValue());
                entity0.setApplication(application);
                updated0.add(entity0);
            }
            saveOrUpdateBatch(updated0);
        }
    }

    @Override
    public ConfigEntity saveConfig(String application, ConfigVO source) {
        String application0 = ObjectUtil.defaultIfNull(source.getApplication(), application);
        Boolean exist = baseMapper.checkExist(source.getKey(), application);
        Assert.isFalse(exist, "lambda.fusion.config.key.existed");
        ConfigEntity target = new ConfigEntity();
        target.setApplication(application0);
        BeanUtils.copyProperties(source, target);
        configsMapper.insert(target);
        if (CollectionUtils.isNotEmpty(source.getOptions())) {
            for (ConfigOptionVO item : source.getOptions()) {
                ConfigOptionEntity optionEntity = new ConfigOptionEntity(item);
                optionEntity.setApplication(application0);
                optionEntity.setPid(target.getId());
                configsOptionMapper.insert(optionEntity);
            }
        }
        LogContext.setDetail("CREATE: " + target.getKey() + "=" + target.getValue());
        return configsMapper.selectConfigById(target.getId());
    }
}
