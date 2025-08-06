package com.lambda.fusion.configs.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.configs.domain.dto.*;
import com.lambda.fusion.configs.domain.entity.ConfigEntity;
import java.util.List;

public interface ConfigService extends IService<ConfigEntity> {

    /**
     * 分页查询配置列表
     *
     * @param page 分页参数
     * @param queryParams 查询参数
     * @return 分页结果
     */
    Page<ConfigEntity> pageConfigs(Page<ConfigEntity> page, ConfigPageQueryDTO queryParams);

    /**
     * 根据条件查询配置列表
     *
     * @param queryDTO 查询参数
     * @return 配置列表
     */
    List<ConfigEntity> listConfigs(ConfigListQueryDTO queryDTO);

    /**
     * 根据条件批量查询配置列表
     *
     * @param queryDTO 批量查询参数
     * @return 配置列表
     */
    List<ConfigEntity> batchQueryConfigs(ConfigQueryDTO queryDTO);

    /**
     * 批量更新配置
     *
     * @param updateDTO 批量更新参数
     * @return 是否更新成功
     */
    boolean batchUpdateConfigs(ConfigBatchUpdateDTO updateDTO);

    /**
     * 更新配置及其选项
     *
     * @param updateDTO 配置更新参数
     * @return 更新后的配置实体
     */
    ConfigEntity updateConfigWithOptions(ConfigUpdateDTO updateDTO);

    /**
     * 保存配置及其选项
     *
     * @param saveDTO 配置保存参数
     * @return 保存后的配置实体
     */
    ConfigEntity saveConfigWithOptions(ConfigSaveDTO saveDTO);
}
