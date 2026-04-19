package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.LlmModelTypeProviderEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmModelTypeProviderMapper extends BaseMapper<LlmModelTypeProviderEntity> {

    default List<LlmModelTypeProviderEntity> selectVisibleByTenantId(String tenantId) {
        LambdaQueryWrapper<LlmModelTypeProviderEntity> query = new LambdaQueryWrapper<LlmModelTypeProviderEntity>()
                .orderByAsc(LlmModelTypeProviderEntity::getSort)
                .orderByAsc(LlmModelTypeProviderEntity::getModelType);
        if (tenantId == null || tenantId.isBlank()) {
            query.isNull(LlmModelTypeProviderEntity::getTenantId);
        } else {
            query.and(wrapper -> wrapper.eq(LlmModelTypeProviderEntity::getTenantId, tenantId)
                    .or()
                    .isNull(LlmModelTypeProviderEntity::getTenantId));
        }
        return selectList(query);
    }

    default List<LlmModelTypeProviderEntity> selectVisibleByProviderCode(String providerCode, String tenantId) {
        LambdaQueryWrapper<LlmModelTypeProviderEntity> query = new LambdaQueryWrapper<LlmModelTypeProviderEntity>()
                .eq(LlmModelTypeProviderEntity::getProviderCode, providerCode)
                .orderByAsc(LlmModelTypeProviderEntity::getSort)
                .orderByAsc(LlmModelTypeProviderEntity::getModelType);
        if (tenantId == null || tenantId.isBlank()) {
            query.isNull(LlmModelTypeProviderEntity::getTenantId);
        } else {
            query.and(wrapper -> wrapper.eq(LlmModelTypeProviderEntity::getTenantId, tenantId)
                    .or()
                    .isNull(LlmModelTypeProviderEntity::getTenantId));
        }
        return selectList(query);
    }
}
