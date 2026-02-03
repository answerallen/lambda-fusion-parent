package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.LlmModelEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * LLM模型 Mapper接口
 *
 * @author Jin
 */
@Mapper
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
}
