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
import com.lambda.fusion.config.model.ConfigEntity;
import com.lambda.fusion.config.model.ConfigOptionEntity;
import com.lambda.fusion.config.refresh.DatabaseContextRefresher;
import com.lambda.fusion.config.service.ConfigChangedService;
import com.lambda.fusion.config.service.ConfigService;
import java.io.Serializable;
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
@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class ConfigServiceImpl extends ServiceImpl<ConfigMapper, ConfigEntity> implements ConfigService {

    /** 配置数据访问接口，用于配置主表操作 */
    private final ConfigMapper configMapper;

    /** 配置选项数据访问接口，用于配置选项表操作 */
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
    @Override
    public boolean batchUpdateConfigs(BatchUpdateConfig updateDTO) {
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
            return true;
        } catch (Exception e) {
            // 异常处理，返回失败状态
            return false;
        }
    }

    /**
     * 更新配置及其选项的具体实现
     *
     * <p>实现配置的完整更新逻辑，包括基本信息和选项的更新，
     * 支持增量更新策略和选项的全量替换。
     *
     * <h3>实现流程：</h3>
     * <ol>
     * <li><strong>存在性验证：</strong>查询并验证目标配置是否存在</li>
     * <li><strong>基本信息更新：</strong>增量更新配置的基本字段</li>
     * <li><strong>选项处理：</strong>删除旧选项，创建新选项</li>
     * <li><strong>日志记录：</strong>记录更新操作的详细信息</li>
     * <li><strong>数据返回：</strong>查询并返回更新后的完整配置</li>
     * </ol>
     *
     * <h3>增量更新策略：</h3>
     * <ul>
     * <li>只更新非空字段，null字段保持原值</li>
     * <li>支持配置键、值、名称、描述、类型的独立更新</li>
     * <li>每个字段都有独立的空值检查</li>
     * <li>保持数据的完整性和一致性</li>
     * </ul>
     *
     * <h3>选项更新策略：</h3>
     * <ul>
     * <li><strong>全量替换：</strong>删除所有旧选项，创建所有新选项</li>
     * <li><strong>级联删除：</strong>先删除关联的选项，再创建新选项</li>
     * <li><strong>事务保证：</strong>选项的删除和创建在同一事务中</li>
     * <li><strong>ID收集：</strong>使用Stream API收集旧选项ID</li>
     * </ul>
     *
     * <h3>异常处理：</h3>
     * <ul>
     * <li>配置不存在时抛出断言异常</li>
     * <li>数据库操作异常会触发事务回滚</li>
     * <li>使用Assert工具进行业务断言</li>
     * <li>异常信息使用国际化键值</li>
     * </ul>
     *
     * <h3>日志记录：</h3>
     * <ul>
     * <li>使用LogContext记录操作详情</li>
     * <li>包含配置键和新值的信息</li>
     * <li>便于操作审计和问题排查</li>
     * </ul>
     *
     * @param updateDTO 配置更新参数对象，包含要更新的字段信息
     * @return 更新后的完整配置实体，包含最新的选项信息
     *
     * @see Assert#notNull(Object, String) 业务断言工具
     * @see LogContext#setDetail(String) 操作日志记录
     */
    @Override
    public ConfigEntity updateConfigWithOptions(UpdateConfig updateDTO) {
        // 查询并验证目标配置存在性
        ConfigEntity target = configMapper.selectConfigById(updateDTO.getId());
        Assert.notNull(target, "lambda.fusion.config.not.found");

        // 增量更新配置基本信息
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

        // 更新配置基本信息
        configMapper.updateById(target);

        // 处理配置选项的全量替换
        if (CollectionUtils.isNotEmpty(updateDTO.getOptions())) {
            // 删除旧的选项
            if (CollectionUtils.isNotEmpty(target.getOptions())) {
                Set<String> oldOptionIds = target.getOptions().stream()
                        .map(ConfigOptionEntity::getId)
                        .collect(Collectors.toSet());
                configOptionMapper.deleteByIds(oldOptionIds);
            }

            // 创建新的选项
            for (SaveConfig.ConfigOptionDTO optionDTO : updateDTO.getOptions()) {
                ConfigOptionEntity optionEntity = new ConfigOptionEntity();
                optionEntity.setApplication(target.getApplication());
                optionEntity.setPid(target.getId());
                optionEntity.setValue(optionDTO.getValue());
                optionEntity.setDescription(optionDTO.getDescription());
                configOptionMapper.insert(optionEntity);
            }
        }

        // 记录操作日志
        LogContext.setDetail("UPDATE: " + target.getKey() + "=" + target.getValue());

        // 触发配置刷新
        configChangedService.execute();
        contextRefresher.doRefresh();

        // 返回更新后的完整配置
        return configMapper.selectConfigById(target.getId());
    }

    /**
     * 保存配置及其选项的具体实现
     *
     * <p>实现新配置的完整创建逻辑，包括基本信息和选项的保存，
     * 支持唯一性检查和事务保证。
     *
     */
    @Override
    public ConfigEntity saveConfigWithOptions(SaveConfig saveDTO) {
        String application = saveDTO.getApplication();

        // 检查配置键的唯一性
        Boolean exist = baseMapper.checkExist(saveDTO.getKey(), application);
        Assert.isFalse(exist, "lambda.fusion.config.key.existed");

        // 构建并保存配置实体
        ConfigEntity target = new ConfigEntity();
        target.setApplication(application);
        target.setKey(saveDTO.getKey());
        target.setValue(saveDTO.getValue());
        target.setName(saveDTO.getName());
        target.setDescription(saveDTO.getDescription());
        target.setType(saveDTO.getType());

        // 保存配置主体信息
        configMapper.insert(target);

        // 批量保存配置选项
        if (CollectionUtils.isNotEmpty(saveDTO.getOptions())) {
            for (SaveConfig.ConfigOptionDTO optionDTO : saveDTO.getOptions()) {
                ConfigOptionEntity optionEntity = new ConfigOptionEntity();
                optionEntity.setApplication(application);
                optionEntity.setPid(target.getId());
                optionEntity.setValue(optionDTO.getValue());
                optionEntity.setDescription(optionDTO.getDescription());
                configOptionMapper.insert(optionEntity);
            }
        }

        // 记录创建操作日志
        LogContext.setDetail("CREATE: " + target.getKey() + "=" + target.getValue());

        // 返回完整的配置信息
        return configMapper.selectConfigById(target.getId());
    }
}
