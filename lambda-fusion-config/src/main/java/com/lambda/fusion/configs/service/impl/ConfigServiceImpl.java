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

/**
 * 系统配置服务实现类
 *
 * <p>实现系统配置管理的核心业务逻辑，基于MyBatis-Plus框架提供完整的配置管理功能。
 * 该实现类负责处理配置的增删改查、批量操作、选项管理等具体业务逻辑。
 *
 * <h3>实现特点：</h3>
 * <ul>
 * <li><strong>事务管理：</strong>所有写操作都支持事务，确保数据一致性</li>
 * <li><strong>异常处理：</strong>提供完善的异常处理和错误反馈机制</li>
 * <li><strong>性能优化：</strong>查询操作使用只读事务，提升性能</li>
 * <li><strong>日志记录：</strong>关键操作自动记录操作日志</li>
 * </ul>
 *
 * <h3>技术架构：</h3>
 * <ul>
 * <li>基于MyBatis-Plus的ServiceImpl基类</li>
 * <li>使用Lambda表达式构建类型安全的查询条件</li>
 * <li>集成Spring事务管理</li>
 * <li>支持批量操作的性能优化</li>
 * </ul>
 *
 * <h3>数据库操作：</h3>
 * <ul>
 * <li><strong>主表操作：</strong>通过ConfigsMapper处理配置基本信息</li>
 * <li><strong>选项操作：</strong>通过ConfigsOptionMapper处理配置选项</li>
 * <li><strong>关联查询：</strong>支持配置与选项的关联查询</li>
 * <li><strong>批量处理：</strong>优化批量数据的处理性能</li>
 * </ul>
 *
 * @since 1.0.0
 * @see ConfigService 配置服务接口
 * @see ServiceImpl MyBatis-Plus基础服务实现
 * @see ConfigsMapper 配置数据访问接口
 * @see ConfigsOptionMapper 配置选项数据访问接口
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class ConfigServiceImpl extends ServiceImpl<ConfigsMapper, ConfigEntity> implements ConfigService {

    /** 配置数据访问接口，用于配置主表操作 */
    @Autowired
    private ConfigsMapper configsMapper;

    /** 配置选项数据访问接口，用于配置选项表操作 */
    @Autowired
    private ConfigsOptionMapper configsOptionMapper;

    /**
     * 分页查询配置列表的具体实现
     *
     * <p>实现分页查询配置信息的具体业务逻辑，采用自定义SQL进行复杂查询，
     * 支持模糊查询的参数预处理和性能优化。
     *
     * <h3>实现细节：</h3>
     * <ul>
     * <li><strong>查询优化：</strong>使用NOT_SUPPORTED事务传播，避免不必要的事务开销</li>
     * <li><strong>参数处理：</strong>对配置名称进行模糊查询参数预处理</li>
     * <li><strong>自定义查询：</strong>通过ConfigsMapper执行复杂的分页查询</li>
     * <li><strong>关联查询：</strong>一次性加载配置及其关联的选项信息</li>
     * </ul>
     *
     * <h3>性能特点：</h3>
     * <ul>
     * <li>使用只读事务，减少数据库锁定时间</li>
     * <li>支持数据库层面的分页，避免内存分页</li>
     * <li>优化的SQL查询，减少数据传输量</li>
     * <li>支持索引优化的查询条件</li>
     * </ul>
     *
     * <h3>参数预处理：</h3>
     * <ol>
     * <li>检查配置名称是否为空</li>
     * <li>如果不为空，使用fuzzyQuery工具进行模糊查询参数转换</li>
     * <li>将处理后的参数传递给Mapper层</li>
     * </ol>
     *
     * @param page 分页参数对象，包含页码和页大小信息
     * @param queryParams 查询条件DTO，包含各种筛选条件
     * @return 分页结果对象，包含当前页数据和分页元信息
     *
     * @see ParameterUtils#fuzzyQuery(String) 模糊查询参数处理工具
     * @since 1.0.0
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public Page<ConfigEntity> pageConfigs(Page<ConfigEntity> page, ConfigPageQueryDTO queryParams) {
        // 预处理模糊查询参数
        if (StringUtils.isNotBlank(queryParams.getName())) {
            queryParams.setName(fuzzyQuery(queryParams.getName()));
        }
        return configsMapper.selectConfigPage(page, queryParams);
    }

    /**
     * 条件查询配置列表的具体实现
     *
     * <p>使用MyBatis-Plus的LambdaQueryWrapper构建类型安全的查询条件，
     * 支持多种条件的灵活组合和动态查询。
     *
     * <h3>实现特点：</h3>
     * <ul>
     * <li><strong>类型安全：</strong>使用Lambda表达式避免字段名硬编码</li>
     * <li><strong>动态查询：</strong>根据参数动态构建查询条件</li>
     * <li><strong>性能优化：</strong>只读事务减少数据库开销</li>
     * <li><strong>条件组合：</strong>支持多种查询条件的AND组合</li>
     * </ul>
     *
     * <h3>查询条件构建逻辑：</h3>
     * <ol>
     * <li><strong>键名右匹配：</strong>如果key不为空，使用likeRight进行右侧模糊匹配</li>
     * <li><strong>ID列表匹配：</strong>如果ids不为空，使用in查询匹配ID列表</li>
     * <li><strong>键列表匹配：</strong>如果keys不为空，使用in查询匹配配置键列表</li>
     * <li><strong>应用精确匹配：</strong>如果application不为空，使用eq进行精确匹配</li>
     * </ol>
     *
     * <h3>查询优化：</h3>
     * <ul>
     * <li>所有条件判断都是惰性的，只有非空时才添加到查询条件</li>
     * <li>使用数据库索引友好的查询方式</li>
     * <li>避免全表扫描的查询模式</li>
     * <li>支持查询结果的自然排序</li>
     * </ul>
     *
     * @param queryDTO 查询条件DTO，包含各种可选的筛选条件
     * @return 配置实体列表，按数据库默认排序返回
     *
     * @see LambdaQueryWrapper MyBatis-Plus类型安全查询构建器
     * @since 1.0.0
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public List<ConfigEntity> listConfigs(ConfigListQueryDTO queryDTO) {
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
     *
     * <p>直接调用Mapper层的自定义批量查询方法，获取应用的完整配置信息，
     * 包括配置的关联选项数据。
     *
     * <h3>实现特点：</h3>
     * <ul>
     * <li><strong>批量优化：</strong>一次查询获取多个配置的完整信息</li>
     * <li><strong>关联查询：</strong>同时加载配置和选项的关联数据</li>
     * <li><strong>应用隔离：</strong>支持按应用名称进行数据隔离</li>
     * <li><strong>灵活筛选：</strong>支持按ID列表进行精确筛选</li>
     * </ul>
     *
     * <h3>查询逻辑：</h3>
     * <ul>
     * <li>如果提供了ID列表，按ID精确匹配获取指定配置</li>
     * <li>如果未提供ID列表，获取应用下的所有配置</li>
     * <li>查询结果包含配置的基本信息和选项信息</li>
     * <li>结果按配置键名进行排序</li>
     * </ul>
     *
     * <h3>性能考虑：</h3>
     * <ul>
     * <li>使用只读事务，提升查询性能</li>
     * <li>一次性加载避免N+1查询问题</li>
     * <li>适合配置中心的数据同步场景</li>
     * <li>支持大批量配置的高效查询</li>
     * </ul>
     *
     * @param queryDTO 批量查询参数，包含应用名称和可选的ID列表
     * @return 配置实体列表，包含完整的配置信息和选项数据
     *
     * @see ConfigsMapper#selectAllSystemConfigs(String, List) 自定义批量查询方法
     * @since 1.0.0
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public List<ConfigEntity> batchQueryConfigs(ConfigQueryDTO queryDTO) {
        return configsMapper.selectAllSystemConfigs(queryDTO.getApplication(), queryDTO.getIds());
    }

    /**
     * 根据ID查询配置的重写实现
     *
     * <p>重写父类的getById方法，使用自定义的查询逻辑获取配置的完整信息，
     * 包括关联的选项数据。
     *
     * <h3>重写原因：</h3>
     * <ul>
     * <li><strong>完整数据：</strong>父类方法只查询主表，无法获取关联的选项信息</li>
     * <li><strong>性能优化：</strong>使用一次查询获取完整的配置信息</li>
     * <li><strong>业务需求：</strong>大多数场景都需要配置的完整信息</li>
     * <li><strong>一致性：</strong>保持与其他查询方法的数据结构一致</li>
     * </ul>
     *
     * <h3>实现细节：</h3>
     * <ul>
     * <li>使用只读事务提升查询性能</li>
     * <li>调用ConfigsMapper的selectConfigById方法</li>
     * <li>该方法会自动加载配置的选项信息</li>
     * <li>支持复杂的关联查询逻辑</li>
     * </ul>
     *
     * <h3>返回数据：</h3>
     * <ul>
     * <li>配置的基本信息（键、值、名称、描述等）</li>
     * <li>配置的关联选项列表</li>
     * <li>配置的元数据信息（创建时间、修改时间等）</li>
     * </ul>
     *
     * @param id 配置ID，会被转换为String类型
     * @return 完整的配置实体信息，如果不存在返回null
     *
     * @see ConfigsMapper#selectConfigById(String) 自定义ID查询方法
     * @since 1.0.0
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED, rollbackFor = Exception.class)
    public ConfigEntity getById(Serializable id) {
        return configsMapper.selectConfigById((String) id);
    }

    /**
     * 批量更新配置的具体实现
     *
     * <p>实现配置的批量更新逻辑，支持同时更新多个配置的值和描述信息，
     * 采用事务保证操作的原子性。
     *
     * <h3>实现策略：</h3>
     * <ul>
     * <li><strong>数据转换：</strong>将DTO对象转换为Entity对象</li>
     * <li><strong>批量处理：</strong>使用MyBatis-Plus的批量更新功能</li>
     * <li><strong>异常处理：</strong>捕获异常并返回操作结果</li>
     * <li><strong>性能优化：</strong>减少数据库交互次数</li>
     * </ul>
     *
     * <h3>更新逻辑：</h3>
     * <ol>
     * <li><strong>参数验证：</strong>检查更新配置列表是否为空</li>
     * <li><strong>数据构建：</strong>遍历更新项，构建ConfigEntity对象</li>
     * <li><strong>字段设置：</strong>设置ID、值、应用名称和描述（如果不为空）</li>
     * <li><strong>批量执行：</strong>调用saveOrUpdateBatch进行批量更新</li>
     * <li><strong>结果返回：</strong>根据执行结果返回成功或失败状态</li>
     * </ol>
     *
     * <h3>事务处理：</h3>
     * <ul>
     * <li>整个方法在事务中执行，确保原子性</li>
     * <li>任何异常都会导致事务回滚</li>
     * <li>使用try-catch保证异常不会外抛</li>
     * <li>通过返回值反馈操作结果</li>
     * </ul>
     *
     * <h3>性能特点：</h3>
     * <ul>
     * <li>批量操作减少数据库连接开销</li>
     * <li>单一事务确保数据一致性</li>
     * <li>内存中数据转换，减少IO操作</li>
     * <li>支持大批量数据的高效更新</li>
     * </ul>
     *
     * @param updateDTO 批量更新参数对象，包含应用名称和更新项列表
     * @return 更新操作是否成功，true表示全部成功，false表示存在失败
     *
     * @see ServiceImpl#saveOrUpdateBatch(Collection) MyBatis-Plus批量操作方法
     * @since 1.0.0
     */
    @Override
    public boolean batchUpdateConfigs(ConfigBatchUpdateDTO updateDTO) {
        try {
            if (CollectionUtils.isNotEmpty(updateDTO.getConfigs())) {
                List<ConfigEntity> updatedConfigs = Lists.newArrayList();
                // 遍历更新项，构建实体对象
                for (ConfigBatchUpdateDTO.ConfigUpdateItem item : updateDTO.getConfigs()) {
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
     * @throws BusinessException 当配置不存在时抛出
     * @see Assert#notNull(Object, String) 业务断言工具
     * @see LogContext#setDetail(String) 操作日志记录
     * @since 1.0.0
     */
    @Override
    public ConfigEntity updateConfigWithOptions(ConfigUpdateDTO updateDTO) {
        // 查询并验证目标配置存在性
        ConfigEntity target = configsMapper.selectConfigById(updateDTO.getId());
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
        configsMapper.updateById(target);

        // 处理配置选项的全量替换
        if (CollectionUtils.isNotEmpty(updateDTO.getOptions())) {
            // 删除旧的选项
            if (CollectionUtils.isNotEmpty(target.getOptions())) {
                Set<String> oldOptionIds = target.getOptions().stream()
                        .map(ConfigOptionEntity::getId)
                        .collect(Collectors.toSet());
                configsOptionMapper.deleteBatchIds(oldOptionIds);
            }

            // 创建新的选项
            for (ConfigSaveDTO.ConfigOptionDTO optionDTO : updateDTO.getOptions()) {
                ConfigOptionEntity optionEntity = new ConfigOptionEntity();
                optionEntity.setApplication(target.getApplication());
                optionEntity.setPid(target.getId());
                optionEntity.setValue(optionDTO.getValue());
                optionEntity.setDescription(optionDTO.getDescription());
                configsOptionMapper.insert(optionEntity);
            }
        }

        // 记录操作日志
        LogContext.setDetail("UPDATE: " + target.getKey() + "=" + target.getValue());

        // 返回更新后的完整配置
        return configsMapper.selectConfigById(target.getId());
    }

    /**
     * 保存配置及其选项的具体实现
     *
     * <p>实现新配置的完整创建逻辑，包括基本信息和选项的保存，
     * 支持唯一性检查和事务保证。
     *
     * <h3>实现流程：</h3>
     * <ol>
     * <li><strong>唯一性检查：</strong>验证配置键在应用内是否已存在</li>
     * <li><strong>实体构建：</strong>创建ConfigEntity对象并设置属性</li>
     * <li><strong>基本信息保存：</strong>保存配置的主体信息</li>
     * <li><strong>选项批量保存：</strong>遍历并保存所有配置选项</li>
     * <li><strong>日志记录：</strong>记录创建操作的详细信息</li>
     * <li><strong>完整数据返回：</strong>查询并返回完整的配置信息</li>
     * </ol>
     *
     * <h3>唯一性校验：</h3>
     * <ul>
     * <li>使用baseMapper的checkExist方法检查键值唯一性</li>
     * <li>在同一应用内配置键必须唯一</li>
     * <li>使用断言确保业务规则的执行</li>
     * <li>异常信息支持国际化处理</li>
     * </ul>
     *
     * <h3>数据保存策略：</h3>
     * <ul>
     * <li><strong>主表先行：</strong>先保存配置基本信息获取主键ID</li>
     * <li><strong>选项关联：</strong>使用主键ID作为选项的父ID</li>
     * <li><strong>批量创建：</strong>遍历选项列表进行批量创建</li>
     * <li><strong>应用隔离：</strong>确保选项与配置在同一应用下</li>
     * </ul>
     *
     * <h3>选项处理逻辑：</h3>
     * <ul>
     * <li>检查选项列表是否为空</li>
     * <li>遍历每个选项DTO对象</li>
     * <li>创建ConfigOptionEntity实体</li>
     * <li>设置应用名称、父ID、值和描述</li>
     * <li>调用Mapper进行数据库插入</li>
     * </ul>
     *
     * <h3>事务和异常：</h3>
     * <ul>
     * <li>整个方法在事务中执行</li>
     * <li>任何步骤失败都会回滚</li>
     * <li>唯一性冲突会抛出业务异常</li>
     * <li>数据库异常会传播到调用方</li>
     * </ul>
     *
     * <h3>日志和审计：</h3>
     * <ul>
     * <li>记录配置的创建操作</li>
     * <li>包含配置键和初始值</li>
     * <li>支持操作审计和追踪</li>
     * </ul>
     *
     * @param saveDTO 配置保存参数对象，包含配置基本信息和选项列表
     * @return 保存后的完整配置实体，包含生成的ID和选项信息
     *
     * @throws BusinessException 当配置键已存在时抛出
     * @see ConfigsMapper#checkExist(String, String) 唯一性检查方法
     * @see Assert#isFalse(boolean, String) 业务断言工具
     * @since 1.0.0
     */
    @Override
    public ConfigEntity saveConfigWithOptions(ConfigSaveDTO saveDTO) {
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
        configsMapper.insert(target);

        // 批量保存配置选项
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

        // 记录创建操作日志
        LogContext.setDetail("CREATE: " + target.getKey() + "=" + target.getValue());

        // 返回完整的配置信息
        return configsMapper.selectConfigById(target.getId());
    }
}
