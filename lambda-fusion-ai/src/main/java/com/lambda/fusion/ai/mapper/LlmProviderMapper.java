package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.LlmProviderEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmProviderMapper extends BaseMapper<LlmProviderEntity> {

    default List<LlmProviderEntity> selectVisibleByTenantId(String tenantId) {
        LambdaQueryWrapper<LlmProviderEntity> query = new LambdaQueryWrapper<LlmProviderEntity>()
                .orderByAsc(LlmProviderEntity::getSort)
                .orderByAsc(LlmProviderEntity::getDisplayName)
                .orderByAsc(LlmProviderEntity::getCode);
        if (tenantId == null || tenantId.isBlank()) {
            query.isNull(LlmProviderEntity::getTenantId);
        } else {
            query.and(wrapper ->
                    wrapper.eq(LlmProviderEntity::getTenantId, tenantId).or().isNull(LlmProviderEntity::getTenantId));
        }
        return selectList(query);
    }

    default LlmProviderEntity selectVisibleByCode(String code, String tenantId) {
        LambdaQueryWrapper<LlmProviderEntity> query = new LambdaQueryWrapper<LlmProviderEntity>()
                .eq(LlmProviderEntity::getCode, code)
                .last("limit 1");
        if (tenantId == null || tenantId.isBlank()) {
            query.isNull(LlmProviderEntity::getTenantId);
        } else {
            query.and(wrapper ->
                    wrapper.eq(LlmProviderEntity::getTenantId, tenantId).or().isNull(LlmProviderEntity::getTenantId));
        }
        return selectOne(query);
    }
}
