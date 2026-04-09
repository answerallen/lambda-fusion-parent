package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.PromptTemplateEntity;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplateEntity> {

    /**
     * 按分类查询模板列表
     * @param category 分类
     * @return 模板列表
     */
    List<PromptTemplateEntity> listByCategory(@Param("category") String category);

    /**
     * 查询系统模板列表
     * @return 系统模板列表
     */
    List<PromptTemplateEntity> listSystemTemplates();

    /**
     * 按租户查询模板
     * @param tenantId 租户ID
     * @return 模板列表
     */
    List<PromptTemplateEntity> selectByTenantId(@Param("tenantId") Long tenantId);

    /**
     * 查询公开模板
     * @return 公开模板列表
     */
    List<PromptTemplateEntity> selectPublicTemplates();

    /**
     * 按所有者查询模板
     * @param ownerUserId 所有者用户ID
     * @return 模板列表
     */
    List<PromptTemplateEntity> selectByOwnerUserId(@Param("ownerUserId") Long ownerUserId);

    /**
     * 批量更新使用次数
     * @param list 包含templateId、usageIncrement的统计对象列表
     * @return 更新数量
     */
    int updateUsageCountBatch(@Param("list") List<Map<String, Object>> list);

    /**
     * 统计各分类模板数
     * @return List<Map<category, is_system, count>>
     */
    @MapKey("category")
    List<Map<String, Object>> countByCategoryGroupByIsSystem();
}
