package com.lambda.fusion.ai.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.LlmModelEntity;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * LLM模型 Mapper接口
 *
 * @author Jin
 */
@Mapper
@DS("#{@aiProperties.dataSource.name}")
public interface LlmModelMapper extends BaseMapper<LlmModelEntity> {

    /**
     * 根据模型类型查询模型列表
     */
    List<LlmModelEntity> listByModelType(@Param("modelType") String modelType);

    /**
     * 根据提供商查询模型列表
     */
    List<LlmModelEntity> listByProvider(@Param("provider") String provider);

    /**
     * 查询默认模型
     */
    LlmModelEntity selectDefaultModel(@Param("modelType") String modelType);

    /**
     * 根据modelId查询
     */
    LlmModelEntity selectByModelId(@Param("modelId") String modelId);

    /**
     * 查询启用的模型
     * @param modelType 模型类型(可选)
     * @return 启用的模型列表
     */
    List<LlmModelEntity> selectEnabledModels(@Param("modelType") String modelType);

    /**
     * 按模型类型和提供商查询
     * @param modelType 模型类型
     * @param provider 提供商
     * @return 模型列表
     */
    List<LlmModelEntity> selectByModelTypeAndProvider(
            @Param("modelType") String modelType, @Param("provider") String provider);

    /**
     * 批量更新调用统计
     * @param list 包含modelId、callCount、tokenCount、cost的统计对象列表
     * @return 更新数量
     */
    int updateTotalCallsBatch(@Param("list") List<Map<String, Object>> list);

    /**
     * 统计各提供商模型数
     * @return List<Map<provider, model_type, count>>
     */
    @MapKey("provider")
    List<Map<String, Object>> countByProviderGroupByModelType();
}
