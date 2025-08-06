package com.lambda.fusion.configs.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.configs.domain.dto.Parameters;
import com.lambda.fusion.configs.domain.entity.ConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ConfigsMapper extends BaseMapper<ConfigEntity> {

    /**
     * 查询所有的配置文件
     *
     * @param application 应用名称
     * @param ids         配置ID列表
     */
    List<ConfigEntity> selectAllSystemConfigs(@Param("application") String application, @Param("ids") Collection<String> ids);

    /**
     * 分页查询配置项
     *
     * @param pagination 分页信息
     * @param parameters 查询参数
     */
    Page<ConfigEntity> selectConfigPage(Page<ConfigEntity> pagination, Parameters parameters);

    /**
     * 根据ID查询配置详情
     *
     * @param id 配置id
     */
    ConfigEntity selectConfigById(@Param("id") String id);

    /**
     * 检查key是否存在
     *
     * @param key         配置信息键
     * @param application 模块名
     */
    Boolean checkExist(@Param("key") String key, @Param("application") String application);
}
