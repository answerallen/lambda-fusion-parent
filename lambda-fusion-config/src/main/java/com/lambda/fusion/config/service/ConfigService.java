package com.lambda.fusion.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.config.model.*;
import com.lambda.fusion.config.model.entity.ConfigEntity;
import com.lambda.fusion.config.model.entity.ConfigOptionEntity;
import java.util.Collection;
import java.util.List;

/**
 * 系统配置服务接口
 *
 * <p>提供系统配置管理的核心业务逻辑，包括配置的增删改查、批量操作、
 * 选项管理等功能。该服务是配置管理系统的业务核心层。
 *
 * @see ConfigEntity 配置实体类
 * @see IService MyBatis-Plus基础服务接口
 */
public interface ConfigService extends IService<ConfigEntity> {

    /**
     * 分页查询配置列表
     *
     * <p>支持按条件分页查询系统配置信息，主要用于管理后台的配置管理界面。
     *
     *
     * @param page 分页参数，包含当前页码和每页大小，不能为null
     * @param queryParams 查询条件DTO，包含配置名称、应用名称等查询条件，支持参数校验
     * @return 分页结果，包含配置实体列表和分页元数据
     *
     * @throws IllegalArgumentException 当分页参数不合法时抛出
     * @see QueryConfigPage 分页查询条件参数说明
     * @see Page MyBatis-Plus分页对象
     */
    Page<ConfigEntity> pageConfigs(Page<ConfigEntity> page, QueryConfigPage queryParams);

    /**
     * 分页查询配置列表（使用LambdaQueryWrapper）
     *
     * <p>使用LambdaQueryWrapper进行分页查询，支持更灵活的排序和查询条件。
     *
     * @param page 分页参数，包含当前页码和每页大小，不能为null
     * @param wrapper LambdaQueryWrapper查询条件，支持复杂查询逻辑
     */
    Page<ConfigEntity> page(Page<ConfigEntity> page, LambdaQueryWrapper<ConfigEntity> wrapper);

    /**
     * 根据条件查询配置列表
     *
     * <p>根据多种条件组合查询配置列表，支持精确匹配和模糊查询，不分页返回所有匹配结果。
     *
     *
     * @param queryDTO 查询条件DTO，所有字段均为可选，支持多种查询方式组合
     * @return 配置实体列表，如果没有匹配结果则返回空列表，不返回null
     *
     * @throws IllegalArgumentException 当参数格式错误时抛出
     * @see QueryConfigList 查询条件详细说明
     */
    List<ConfigEntity> listConfigs(QueryConfigList queryDTO);

    /**
     * 根据条件批量查询配置列表
     *
     * <p>主要用于系统间的配置同步和配置中心数据获取，支持按应用名称和配置ID列表进行批量查询。
     *
     * @param queryDTO 批量查询参数，包含应用名称和配置ID列表
     * @return 配置实体列表，按键名排序，包含完整的配置信息
     *
     * @throws IllegalArgumentException 当参数不合法时抛出
     * @see QueryConfig 批量查询参数说明
     */
    List<ConfigEntity> batchQueryConfigs(QueryConfig queryDTO);

    /**
     * 批量更新配置
     *
     * <p>批量更新多个配置的值和描述信息，支持事务保证，确保操作的原子性。
     *
     *
     * @param updateDTO 批量更新参数，包含应用名称和配置更新项列表
     * @see BatchUpdateConfig 批量更新参数说明
     */
    void batchUpdateConfigs(BatchUpdateConfig updateDTO);

    /**
     * 更新配置及其选项
     *
     * <p>根据配置ID更新配置的基本信息和选项，支持增量更新，只更新提供的字段。
     */
    void updateConfigWithOptions(UpdateConfig updateDTO);

    /**
     * 保存配置及其选项
     *
     * <p>创建新的系统配置项，支持同时保存配置基本信息和配置选项。
     *
     * @param saveDTO 配置保存参数，包含配置基本信息和选项，必须通过参数校验
     * @return 保存后的完整配置实体，包含生成的ID和创建时间
     *
     * @see SaveConfig 保存参数详细说明
     */
    void saveConfigWithOptions(SaveConfig saveDTO);

    /**
     * 根据ID查询配置选项
     *
     * <p>根据配置选项的唯一ID查询详细信息。
     *
     * @param id 配置选项的唯一标识符，不能为null
     * @return 配置选项实体，如果未找到则返回null
     *
     * @throws IllegalArgumentException 当ID为null时抛出
     */
    ConfigOptionEntity getConfigOptionById(String id);

    /**
     * 更新配置选项
     *
     * <p>根据配置选项实体更新数据库中的配置选项信息。
     *
     * @param configOption 包含更新信息的配置选项实体，必须包含ID
     *
     * @throws IllegalArgumentException 当实体ID为null时抛出
     */
    void updateConfigOption(ConfigOptionEntity configOption);

    /**
     * 根据ID删除配置选项
     *
     * <p>根据配置选项的唯一ID删除数据库中的配置选项记录。
     *
     * @param id 配置选项的唯一标识符，不能为null
     *
     * @throws IllegalArgumentException 当ID为null时抛出
     */
    void removeConfigOptionById(String id);

    /**
     * 根据ID批量删除配置选项
     *
     * <p>根据配置选项的唯一ID列表批量删除数据库中的配置选项记录。
     *
     * @param ids 配置选项的唯一标识符列表，不能为null或空
     *
     * @throws IllegalArgumentException 当ID列表为null或空时抛出
     */
    void removeConfigOptionsByIds(Collection<String> ids);
}
