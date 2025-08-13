package com.lambda.fusion.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.config.domain.dto.*;
import com.lambda.fusion.config.domain.entity.ConfigEntity;
import java.util.List;

/**
 * 系统配置服务接口
 *
 * <p>提供系统配置管理的核心业务逻辑，包括配置的增删改查、批量操作、
 * 选项管理等功能。该服务是配置管理系统的业务核心层。
 *
 * <h3>主要功能：</h3>
 * <ul>
 * <li><strong>配置查询：</strong>支持分页查询、条件查询、批量查询等多种查询方式</li>
 * <li><strong>配置管理：</strong>配置的新增、更新、删除操作</li>
 * <li><strong>选项管理：</strong>配置选项的关联保存和更新</li>
 * <li><strong>批量操作：</strong>支持配置的批量查询和批量更新</li>
 * </ul>
 *
 * <h3>设计特点：</h3>
 * <ul>
 * <li>基于MyBatis-Plus的IService接口，提供基础CRUD能力</li>
 * <li>使用DTO模式进行参数传递，确保类型安全</li>
 * <li>支持事务管理，保证数据一致性</li>
 * <li>提供灵活的查询条件组合</li>
 * </ul>
 *
 * @since 1.0.0
 * @see ConfigEntity 配置实体类
 * @see IService MyBatis-Plus基础服务接口
 */
public interface ConfigService extends IService<ConfigEntity> {

    /**
     * 分页查询配置列表
     *
     * <p>支持按条件分页查询系统配置信息，主要用于管理后台的配置管理界面。
     *
     * <h3>功能特性：</h3>
     * <ul>
     * <li>支持按配置名称模糊查询</li>
     * <li>支持按应用名称精确筛选</li>
     * <li>支持租户配置隔离</li>
     * <li>自动处理分页参数</li>
     * </ul>
     *
     * <h3>查询条件：</h3>
     * <ul>
     * <li><strong>name：</strong>配置名称模糊查询，支持右侧匹配</li>
     * <li><strong>application：</strong>应用名称精确匹配</li>
     * <li><strong>tenantId：</strong>租户ID，支持多租户数据隔离</li>
     * </ul>
     *
     * <h3>业务逻辑：</h3>
     * <ol>
     * <li>接收分页参数和查询条件</li>
     * <li>处理模糊查询条件（如配置名称）</li>
     * <li>执行数据库分页查询</li>
     * <li>返回分页结果包含总数和当前页数据</li>
     * </ol>
     *
     * @param page 分页参数，包含当前页码和每页大小，不能为null
     * @param queryParams 查询条件DTO，包含配置名称、应用名称等查询条件，支持参数校验
     * @return 分页结果，包含配置实体列表和分页元数据
     *
     * @throws IllegalArgumentException 当分页参数不合法时抛出
     * @see ConfigPageQueryDTO 分页查询条件参数说明
     * @see Page MyBatis-Plus分页对象
     * @since 1.0.0
     */
    Page<ConfigEntity> pageConfigs(Page<ConfigEntity> page, ConfigPageQueryDTO queryParams);

    /**
     * 分页查询配置列表（使用LambdaQueryWrapper）
     *
     * <p>使用LambdaQueryWrapper进行分页查询，支持更灵活的排序和查询条件。
     *
     * @param page 分页参数，包含当前页码和每页大小，不能为null
     * @param wrapper LambdaQueryWrapper查询条件，支持复杂查询逻辑
     * @return 分页结果，包含配置实体列表和分页元数据
     * @since 1.0.0
     */
    Page<ConfigEntity> page(Page<ConfigEntity> page, LambdaQueryWrapper<ConfigEntity> wrapper);

    /**
     * 根据条件查询配置列表
     *
     * <p>根据多种条件组合查询配置列表，支持精确匹配和模糊查询，不分页返回所有匹配结果。
     *
     * <h3>支持的查询条件：</h3>
     * <ul>
     * <li><strong>按键名查询：</strong>支持右侧模糊匹配，如 "spring" 匹配所有以 "spring" 开头的配置键</li>
     * <li><strong>按ID列表查询：</strong>支持List&lt;String&gt;格式的配置ID列表</li>
     * <li><strong>按键列表查询：</strong>支持List&lt;String&gt;格式的配置键列表</li>
     * <li><strong>按应用名查询：</strong>精确匹配应用名称</li>
     * </ul>
     *
     * <h3>查询逻辑：</h3>
     * <ul>
     * <li>所有条件采用AND组合，即同时满足所有非空条件</li>
     * <li>支持灵活的条件组合，未设置的条件将被忽略</li>
     * <li>查询结果按创建时间排序</li>
     * </ul>
     *
     * <h3>使用场景：</h3>
     * <ul>
     * <li>前端下拉框数据获取</li>
     * <li>配置项的批量操作预览</li>
     * <li>配置项的条件筛选</li>
     * </ul>
     *
     * @param queryDTO 查询条件DTO，所有字段均为可选，支持多种查询方式组合
     * @return 配置实体列表，如果没有匹配结果则返回空列表，不返回null
     *
     * @throws IllegalArgumentException 当参数格式错误时抛出
     * @see ConfigListQueryDTO 查询条件详细说明
     * @since 1.0.0
     */
    List<ConfigEntity> listConfigs(ConfigListQueryDTO queryDTO);

    /**
     * 根据条件批量查询配置列表
     *
     * <p>主要用于系统间的配置同步和配置中心数据获取，支持按应用名称和配置ID列表进行批量查询。
     *
     * <h3>查询逻辑：</h3>
     * <ul>
     * <li>如果提供ID列表，按ID精确匹配</li>
     * <li>如果未提供ID列表，返回应用下所有配置</li>
     * <li>结果按配置键名排序</li>
     * <li>包含配置的完整信息和选项数据</li>
     * </ul>
     *
     * <h3>使用场景：</h3>
     * <ul>
     * <li>系统配置的批量导出</li>
     * <li>配置中心数据同步</li>
     * <li>微服务间配置共享</li>
     * <li>配置管理界面数据展示</li>
     * </ul>
     *
     * <h3>性能考虑：</h3>
     * <ul>
     * <li>支持大批量查询，但建议控制ID列表大小</li>
     * <li>查询结果包含关联的选项数据</li>
     * <li>适合一次性获取完整配置数据</li>
     * </ul>
     *
     * @param queryDTO 批量查询参数，包含应用名称和配置ID列表
     * @return 配置实体列表，按键名排序，包含完整的配置信息
     *
     * @throws IllegalArgumentException 当参数不合法时抛出
     * @see ConfigQueryDTO 批量查询参数说明
     * @since 1.0.0
     */
    List<ConfigEntity> batchQueryConfigs(ConfigQueryDTO queryDTO);

    /**
     * 批量更新配置
     *
     * <p>批量更新多个配置的值和描述信息，支持事务保证，确保操作的原子性。
     *
     * <h3>更新逻辑：</h3>
     * <ul>
     * <li>支持同时更新配置值和描述</li>
     * <li>只更新提供的字段，null字段保持原值不变</li>
     * <li>按配置ID精确匹配进行更新</li>
     * <li>所有更新操作在一个事务中执行</li>
     * </ul>
     *
     * <h3>操作特点：</h3>
     * <ul>
     * <li>支持增量更新，提高更新效率</li>
     * <li>事务保证，要么全部成功要么全部失败</li>
     * <li>自动记录操作日志</li>
     * <li>支持大批量操作</li>
     * </ul>
     *
     * <h3>使用场景：</h3>
     * <ul>
     * <li>运维人员批量调整系统参数</li>
     * <li>系统性能优化配置调整</li>
     * <li>应用环境配置切换</li>
     * <li>配置管理界面批量编辑</li>
     * </ul>
     *
     * <h3>注意事项：</h3>
     * <ul>
     * <li>更新失败时会抛出异常并回滚</li>
     * <li>建议在调用后触发配置刷新</li>
     * <li>不支持配置键名的更新</li>
     * </ul>
     *
     * @param updateDTO 批量更新参数，包含应用名称和配置更新项列表
     * @return 更新是否成功，true表示全部更新成功，false表示存在更新失败的项
     *
     * @see ConfigBatchUpdateDTO 批量更新参数说明
     * @since 1.0.0
     */
    boolean batchUpdateConfigs(ConfigBatchUpdateDTO updateDTO);

    /**
     * 更新配置及其选项
     *
     * <p>根据配置ID更新配置的基本信息和选项，支持增量更新，只更新提供的字段。
     *
     * <h3>功能特性：</h3>
     * <ul>
     * <li>支持增量更新，null字段不会更新</li>
     * <li>配置选项全量替换策略</li>
     * <li>自动记录操作日志</li>
     * <li>事务保证数据一致性</li>
     * </ul>
     *
     * <h3>更新内容：</h3>
     * <ul>
     * <li><strong>基本信息：</strong>配置键、值、名称、描述、类型</li>
     * <li><strong>选项管理：</strong>删除原有选项并创建新选项</li>
     * <li><strong>元数据：</strong>自动更新修改时间等信息</li>
     * </ul>
     *
     * <h3>业务逻辑：</h3>
     * <ol>
     * <li>验证配置ID存在性</li>
     * <li>增量更新配置基本信息</li>
     * <li>如果提供了选项，删除原有选项并创建新选项</li>
     * <li>记录操作日志</li>
     * <li>返回更新后的完整配置实体</li>
     * </ol>
     *
     * <h3>选项处理策略：</h3>
     * <ul>
     * <li>如果updateDTO.options不为null，执行全量替换</li>
     * <li>如果updateDTO.options为null，保持原有选项不变</li>
     * <li>如果updateDTO.options为空列表，删除所有选项</li>
     * </ul>
     *
     * @param updateDTO 配置更新参数，支持增量更新，通过参数校验
     * @return 更新后的完整配置实体，包含最新的选项信息
     *
     * @see ConfigUpdateDTO 更新参数详细说明
     * @since 1.0.0
     */
    ConfigEntity updateConfigWithOptions(ConfigUpdateDTO updateDTO);

    /**
     * 保存配置及其选项
     *
     * <p>创建新的系统配置项，支持同时保存配置基本信息和配置选项。
     *
     * <h3>功能特性：</h3>
     * <ul>
     * <li>配置键唯一性检查</li>
     * <li>支持多个配置选项</li>
     * <li>事务保证数据一致性</li>
     * <li>自动记录操作日志</li>
     * </ul>
     *
     * <h3>保存内容：</h3>
     * <ul>
     * <li><strong>基本信息：</strong>应用名称、配置键、值、名称、描述、类型</li>
     * <li><strong>选项信息：</strong>配置的可选值和描述</li>
     * <li><strong>元数据：</strong>创建时间、修改时间等系统字段</li>
     * </ul>
     *
     * <h3>业务逻辑：</h3>
     * <ol>
     * <li>接收配置保存DTO参数</li>
     * <li>校验配置键在应用内的唯一性</li>
     * <li>保存配置基本信息</li>
     * <li>如果存在选项则批量保存配置选项</li>
     * <li>记录操作日志</li>
     * <li>返回完整的配置实体</li>
     * </ol>
     *
     * <h3>数据校验：</h3>
     * <ul>
     * <li>配置键在应用内必须唯一</li>
     * <li>必填字段验证</li>
     * <li>数据格式验证</li>
     * <li>业务规则验证</li>
     * </ul>
     *
     * <h3>异常情况：</h3>
     * <ul>
     * <li>配置键已存在：抛出业务异常</li>
     * <li>参数校验失败：抛出参数异常</li>
     * <li>保存操作失败：抛出数据异常</li>
     * </ul>
     *
     * @param saveDTO 配置保存参数，包含配置基本信息和选项，必须通过参数校验
     * @return 保存后的完整配置实体，包含生成的ID和创建时间
     *
     * @see ConfigSaveDTO 保存参数详细说明
     * @since 1.0.0
     */
    ConfigEntity saveConfigWithOptions(ConfigSaveDTO saveDTO);
}
