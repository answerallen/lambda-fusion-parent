package com.lambda.fusion.config.service.impl;

import static com.lambda.fusion.core.utils.SqlParamUtils.fuzzyQuery;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.google.common.collect.Lists;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.cloud.logger.context.LogContext;
import com.lambda.fusion.config.mapper.ConfigMapper;
import com.lambda.fusion.config.mapper.ConfigOptionMapper;
import com.lambda.fusion.config.model.*;
import com.lambda.fusion.config.refresh.DatabaseContextRefresher;
import com.lambda.fusion.config.service.ConfigChangedService;
import com.lambda.fusion.config.service.ConfigService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统配置服务实现类
 */
@SuppressFBWarnings("EI_EXPOSE_REP2")
@Service
@RequiredArgsConstructor
public class ConfigServiceImpl extends ServiceImpl<ConfigMapper, ConfigEntity> implements ConfigService {

    /**
     * 配置数据访问接口，用于配置主表操作
     */
    private final ConfigMapper configMapper;

    /**
     * 配置选项数据访问接口，用于配置选项表操作
     */
    private final ConfigOptionMapper configOptionMapper;

    /**
     * 数据库上下文刷新器
     */
    private final DatabaseContextRefresher contextRefresher;

    /**
     * 配置变更服务
     */
    private final ConfigChangedService configChangedService;

    /**
     * 分页查询配置列表的具体实现
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public Page<ConfigEntity> pageConfigs(Page<ConfigEntity> page, QueryConfigPage queryParams) {
        // 预处理模糊查询参数
        if (StringUtils.isNotBlank(queryParams.getName())) {
            queryParams.setName(fuzzyQuery(queryParams.getName()));
        }
        return configMapper.selectConfigPage(page, queryParams);
    }

    /**
     * 分页查询配置列表（使用LambdaQueryWrapper）
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public Page<ConfigEntity> page(Page<ConfigEntity> page, LambdaQueryWrapper<ConfigEntity> wrapper) {
        return baseMapper.selectPage(page, wrapper);
    }

    /**
     * 条件查询配置列表的具体实现
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public List<ConfigEntity> listConfigs(QueryConfigList queryDTO) {
        // 构建类型安全的查询条件
        LambdaQueryWrapper<ConfigEntity> queryWrapper = Wrappers.lambdaQuery(ConfigEntity.class);

        // 动态添加查询条件
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

    /**
     * 批量查询配置的具体实现
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public List<ConfigEntity> batchQueryConfigs(QueryConfig queryDTO) {
        return configMapper.selectAllSystemConfigs(queryDTO.getApplication(), queryDTO.getIds());
    }

    /**
     * 根据ID查询配置的重写实现
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public ConfigEntity getById(Serializable id) {
        return configMapper.selectConfigById((String) id);
    }

    /**
     * 批量更新配置的具体实现
     *
     * <p>实现配置的批量更新逻辑，支持同时更新多个配置的值和描述信息，
     * 采用事务保证操作的原子性。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void batchUpdateConfigs(BatchUpdateConfig updateDTO) {
        try {
            if (CollectionUtils.isNotEmpty(updateDTO.getConfigs())) {
                List<ConfigEntity> updatedConfigs = Lists.newArrayList();
                // 遍历更新项，构建实体对象
                for (BatchUpdateConfig.ConfigUpdateItem item : updateDTO.getConfigs()) {
                    ConfigEntity entity = new ConfigEntity();
                    entity.setId(item.getId());
                    entity.setValue(item.getValue());
                    entity.setApplication(updateDTO.getApplication());
                    // 描述字段的增量更新
                    if (StringUtils.isNotBlank(item.getDescription())) {
                        entity.setDescription(item.getDescription());
                    }
                    updatedConfigs.add(entity);
                }
                // 执行批量更新
                saveOrUpdateBatch(updatedConfigs);
            }
        } catch (Exception e) {
            // 异常处理，返回失败状态
        }
    }

    /**
     * 更新配置及其选项的具体实现
     *
     * <p>实现配置的完整更新逻辑，包括基本信息和选项的更新，
     * 支持增量更新策略和选项的全量替换。
     *
     * @param updateConfig 配置更新参数对象，包含要更新的字段信息
     * @see Assert#notNull(Object, String) 业务断言工具
     * @see LogContext#setDetail(String) 操作日志记录
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateConfigWithOptions(UpdateConfig updateConfig) {
        // 查询并验证目标配置存在性
        ConfigEntity target = configMapper.selectConfigById(updateConfig.getId());
        Assert.notNull(target, "配置不存在！");

        // 增量更新配置基本信息
        if (StringUtils.isNotBlank(updateConfig.getKey())) {
            target.setKey(updateConfig.getKey());
        }
        if (StringUtils.isNotBlank(updateConfig.getValue())) {
            target.setValue(updateConfig.getValue());
        }
        if (StringUtils.isNotBlank(updateConfig.getName())) {
            target.setName(updateConfig.getName());
        }
        if (StringUtils.isNotBlank(updateConfig.getDescription())) {
            target.setDescription(updateConfig.getDescription());
        }
        if (updateConfig.getType() != null) {
            target.setType(updateConfig.getType());
        }

        target.setUpdateTime(LocalDateTime.now());
        // 更新配置基本信息
        configMapper.updateById(target);

        // 处理配置选项的全量替换
        if (CollectionUtils.isNotEmpty(updateConfig.getOptions())) {
            // 删除旧的选项
            if (CollectionUtils.isNotEmpty(target.getOptions())) {
                Set<String> oldOptionIds = target.getOptions().stream()
                        .map(ConfigOptionEntity::getId)
                        .collect(Collectors.toSet());
                configOptionMapper.deleteByIds(oldOptionIds);
            }

            // 创建新的选项
            for (SaveConfig.ConfigOption optionDTO : updateConfig.getOptions()) {
                ConfigOptionEntity optionEntity = new ConfigOptionEntity();
                optionEntity.setApplication(target.getApplication());
                optionEntity.setPid(target.getId());
                optionEntity.setName(optionDTO.getLabel());
                optionEntity.setValue(optionDTO.getValue());
                optionEntity.setDescription(optionDTO.getDescription());
                configOptionMapper.insert(optionEntity);
            }
        }

        // 记录操作日志
        LogContext.setDetail("UPDATE: " + target.getKey() + "=" + target.getValue());

        // 触发配置刷新
        configChangedService.execute();
        Thread.ofVirtual().start(contextRefresher::doRefresh);
    }

    /**
     * 保存配置及其选项的具体实现
     *
     * <p>实现新配置的完整创建逻辑，包括基本信息和选项的保存，
     * 支持唯一性检查和事务保证。
     *
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveConfigWithOptions(SaveConfig saveConfig) {
        String application = saveConfig.getApplication();

        // 检查配置键的唯一性
        Boolean exist = baseMapper.checkExist(saveConfig.getKey(), application);
        Assert.isFalse(exist, "配置不存在！");

        // 构建并保存配置实体
        ConfigEntity target = saveConfig.toEntity();
        target.setUpdateTime(LocalDateTime.now());
        // 保存配置主体信息
        configMapper.insert(target);

        // 批量保存配置选项
        if (CollectionUtils.isNotEmpty(saveConfig.getOptions())) {
            for (SaveConfig.ConfigOption optionDTO : saveConfig.getOptions()) {
                ConfigOptionEntity optionEntity = new ConfigOptionEntity();
                optionEntity.setApplication(application);
                optionEntity.setPid(target.getId());
                optionEntity.setName(optionDTO.getLabel());
                optionEntity.setValue(optionDTO.getValue());
                optionEntity.setDescription(optionDTO.getDescription());
                configOptionMapper.insert(optionEntity);
            }
        }

        // 记录创建操作日志
        LogContext.setDetail("CREATE: " + target.getKey() + "=" + target.getValue());
    }
}
