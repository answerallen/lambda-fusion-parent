package com.lambda.fusion.configs.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.configs.domain.dto.Parameters;
import com.lambda.fusion.configs.domain.dto.Query;
import com.lambda.fusion.configs.domain.entity.ConfigEntity;
import com.lambda.fusion.configs.domain.vo.ConfigVO;
import java.util.List;
import java.util.Map;

public interface ConfigService extends IService<ConfigEntity> {

    /**
     * 分页查询配置列表
     *
     * @param page
     * @param parameters
     */
    Page<ConfigEntity> page(Page<ConfigEntity> page, Parameters parameters);

    /**
     * 根据查询参数查询配置属性列表
     *
     * @param parameters
     */
    List<ConfigEntity> queryConfigsByConditions(Map<String, Object> parameters);

    /**
     * 根据查询参数查询配置属性列表
     *
     * @param parameters
     */
    List<ConfigEntity> queryConfigsByConditions(Query parameters);

    /**
     * 保存或更新系统配置属性
     *
     * @param application
     * @param updated
     */
    void updateBatchByApplication(String application, List<ConfigEntity> updated);

    /**
     * 同步保存配置与选项
     *
     * @param application
     * @param source
     * @return void
     */
    ConfigEntity saveConfig(String application, ConfigVO source);
}
