package com.lambda.fusion.configs.service.impl;

import static com.lambda.fusion.core.utils.ParameterUtils.fuzzyQuery;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.logger.context.LogContext;
import com.lambda.fusion.configs.domain.dto.*;
import com.lambda.fusion.configs.domain.entity.ConfigEntity;
import com.lambda.fusion.configs.domain.entity.ConfigOptionEntity;
import com.lambda.fusion.configs.mapper.ConfigsMapper;
import com.lambda.fusion.configs.mapper.ConfigsOptionMapper;
import com.lambda.fusion.configs.service.ConfigService;
import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class ConfigServiceImpl extends ServiceImpl<ConfigsMapper, ConfigEntity> implements ConfigService {

    @Autowired
    private ConfigsMapper configsMapper;

    @Autowired
    private ConfigsOptionMapper configsOptionMapper;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public Page<ConfigEntity> pageConfigs(Page<ConfigEntity> page, ConfigPageQueryDTO queryParams) {
        if (StringUtils.isNotBlank(queryParams.getName())) {
            queryParams.setName(fuzzyQuery(queryParams.getName()));
        }
        return configsMapper.selectConfigPage(page, queryParams);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public List<ConfigEntity> listConfigs(ConfigListQueryDTO queryDTO) {
        LambdaQueryWrapper<ConfigEntity> queryWrapper = Wrappers.lambdaQuery(ConfigEntity.class);

        if (StringUtils.isNotBlank(queryDTO.getKey())) {
            queryWrapper.likeRight(ConfigEntity::getKey, queryDTO.getKey());
        }
        if (CollectionUtils.isNotEmpty(queryDTO.getIds())) {
            queryWrapper.in(ConfigEntity::getId, queryDTO.getIds());
        }
        if (CollectionUtils.isNotEmpty(queryDTO.getKeys())) {
            queryWrapper.in(ConfigEntity::getKey, queryDTO.getKeys());
        }
        if (StringUtils.isNotBlank(queryDTO.getApplication())) {
            queryWrapper.eq(ConfigEntity::getApplication, queryDTO.getApplication());
        }

        return list(queryWrapper);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public List<ConfigEntity> batchQueryConfigs(ConfigQueryDTO queryDTO) {
        return configsMapper.selectAllSystemConfigs(queryDTO.getApplication(), queryDTO.getIds());
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public ConfigEntity getById(Serializable id) {
        return configsMapper.selectConfigById((String) id);
    }

    @Override
    public boolean batchUpdateConfigs(ConfigBatchUpdateDTO updateDTO) {
        try {
            if (CollectionUtils.isNotEmpty(updateDTO.getConfigs())) {
                List<ConfigEntity> updatedConfigs = Lists.newArrayList();
                for (ConfigBatchUpdateDTO.ConfigUpdateItem item : updateDTO.getConfigs()) {
                    ConfigEntity entity = new ConfigEntity();
                    entity.setId(item.getId());
                    entity.setValue(item.getValue());
                    entity.setApplication(updateDTO.getApplication());
                    if (StringUtils.isNotBlank(item.getDescription())) {
                        entity.setDescription(item.getDescription());
                    }
                    updatedConfigs.add(entity);
                }
                saveOrUpdateBatch(updatedConfigs);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ConfigEntity updateConfigWithOptions(ConfigUpdateDTO updateDTO) {
        ConfigEntity target = configsMapper.selectConfigById(updateDTO.getId());
        Assert.notNull(target, "lambda.fusion.config.not.found");

        // 更新配置基本信息
        if (StringUtils.isNotBlank(updateDTO.getKey())) {
            target.setKey(updateDTO.getKey());
        }
        if (StringUtils.isNotBlank(updateDTO.getValue())) {
            target.setValue(updateDTO.getValue());
        }
        if (StringUtils.isNotBlank(updateDTO.getName())) {
            target.setName(updateDTO.getName());
        }
        if (StringUtils.isNotBlank(updateDTO.getDescription())) {
            target.setDescription(updateDTO.getDescription());
        }
        if (updateDTO.getType() != null) {
            target.setType(updateDTO.getType());
        }

        configsMapper.updateById(target);

        // 更新配置选项
        if (CollectionUtils.isNotEmpty(updateDTO.getOptions())) {
            // 删除旧的选项
            if (CollectionUtils.isNotEmpty(target.getOptions())) {
                Set<String> oldOptionIds = target.getOptions().stream()
                        .map(ConfigOptionEntity::getId)
                        .collect(Collectors.toSet());
                configsOptionMapper.deleteBatchIds(oldOptionIds);
            }

            // 插入新的选项
            for (ConfigSaveDTO.ConfigOptionDTO optionDTO : updateDTO.getOptions()) {
                ConfigOptionEntity optionEntity = new ConfigOptionEntity();
                optionEntity.setApplication(target.getApplication());
                optionEntity.setPid(target.getId());
                optionEntity.setValue(optionDTO.getValue());
                optionEntity.setDescription(optionDTO.getDescription());
                configsOptionMapper.insert(optionEntity);
            }
        }

        LogContext.setDetail("UPDATE: " + target.getKey() + "=" + target.getValue());
        return configsMapper.selectConfigById(target.getId());
    }

    @Override
    public ConfigEntity saveConfigWithOptions(ConfigSaveDTO saveDTO) {
        String application = saveDTO.getApplication();
        Boolean exist = baseMapper.checkExist(saveDTO.getKey(), application);
        Assert.isFalse(exist, "lambda.fusion.config.key.existed");

        ConfigEntity target = new ConfigEntity();
        target.setApplication(application);
        target.setKey(saveDTO.getKey());
        target.setValue(saveDTO.getValue());
        target.setName(saveDTO.getName());
        target.setDescription(saveDTO.getDescription());
        target.setType(saveDTO.getType());

        configsMapper.insert(target);

        if (CollectionUtils.isNotEmpty(saveDTO.getOptions())) {
            for (ConfigSaveDTO.ConfigOptionDTO optionDTO : saveDTO.getOptions()) {
                ConfigOptionEntity optionEntity = new ConfigOptionEntity();
                optionEntity.setApplication(application);
                optionEntity.setPid(target.getId());
                optionEntity.setValue(optionDTO.getValue());
                optionEntity.setDescription(optionDTO.getDescription());
                configsOptionMapper.insert(optionEntity);
            }
        }

        LogContext.setDetail("CREATE: " + target.getKey() + "=" + target.getValue());
        return configsMapper.selectConfigById(target.getId());
    }
}
