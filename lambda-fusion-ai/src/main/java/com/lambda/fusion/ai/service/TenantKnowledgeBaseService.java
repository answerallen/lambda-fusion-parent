package com.lambda.fusion.ai.service;

import com.lambda.fusion.ai.datasource.AiTenantDataSourceHelper;
import com.lambda.fusion.ai.mapper.KnowledgeBaseMapper;
import com.lambda.fusion.ai.model.KnowledgeBase;
import com.lambda.fusion.ai.model.entity.KnowledgeBaseEntity;
import com.lambda.fusion.datasource.util.DataSourceSwitcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 租户知识库服务
 * <p>
 * 演示如何使用编程式数据源切换访问租户数据源。
 * 使用 AiTenantDataSourceHelper 实现租户数据隔离。
 * </p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 查询指定租户的知识库列表
 * List&lt;KnowledgeBase&gt; kbList = tenantKnowledgeBaseService.listByTenant("1001");
 * 
 * // 查询当前租户的知识库列表
 * List&lt;KnowledgeBase&gt; currentKbList = tenantKnowledgeBaseService.listByCurrentTenant();
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantKnowledgeBaseService {
    
    private final AiTenantDataSourceHelper aiTenantDataSourceHelper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    
    /**
     * 查询指定租户的知识库列表
     * 
     * @param tenantId 租户ID
     * @return 知识库列表
     */
    public List<KnowledgeBase> listByTenant(String tenantId) {
        log.info("Querying knowledge bases for tenant: {}", tenantId);
        
        // 编程式切换到租户数据源
        try (DataSourceSwitcher switcher = aiTenantDataSourceHelper.switchToTenantDataSource(tenantId)) {
            
            // 执行数据库操作（自动路由到租户数据源）
            List<KnowledgeBaseEntity> entities = knowledgeBaseMapper.selectList(null);
            
            log.info("Found {} knowledge bases for tenant: {}", entities.size(), tenantId);
            
            return entities.stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());
                
        } // 自动恢复到之前的数据源
    }
    
    /**
     * 查询当前租户的知识库列表
     * 
     * @return 知识库列表
     */
    public List<KnowledgeBase> listByCurrentTenant() {
        log.info("Querying knowledge bases for current tenant");
        
        // 编程式切换到当前租户数据源
        try (DataSourceSwitcher switcher = aiTenantDataSourceHelper.switchToCurrentTenantDataSource()) {
            
            // 执行数据库操作（自动路由到当前租户数据源）
            List<KnowledgeBaseEntity> entities = knowledgeBaseMapper.selectList(null);
            
            log.info("Found {} knowledge bases for current tenant", entities.size());
            
            return entities.stream()
                .map(this::entityToVO)
                .collect(Collectors.toList());
                
        } // 自动恢复到之前的数据源
    }
    
    /**
     * 检查租户数据源是否存在
     * 
     * @param tenantId 租户ID
     * @return true 如果租户数据源存在
     */
    public boolean tenantDataSourceExists(String tenantId) {
        return aiTenantDataSourceHelper.tenantDataSourceExists(tenantId);
    }
    
    /**
     * 获取租户数据源名称
     * 
     * @param tenantId 租户ID
     * @return 数据源名称
     */
    public String getTenantDataSourceName(String tenantId) {
        return aiTenantDataSourceHelper.getTenantDataSourceName(tenantId);
    }
    
    /**
     * 实体转VO
     */
    private KnowledgeBase entityToVO(KnowledgeBaseEntity entity) {
        KnowledgeBase vo = new KnowledgeBase();
        vo.setId(entity.getId());
        vo.setKbId(entity.getKbId());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setCategory(entity.getCategory());
        vo.setTenantId(entity.getTenantId());
        vo.setOwnerUserId(entity.getOwnerUserId());
        vo.setEmbeddingModel(entity.getEmbeddingModel());
        vo.setEmbeddingDimension(entity.getEmbeddingDimension());
        vo.setVectorTableName(entity.getVectorTableName());
        vo.setChunkSize(entity.getChunkSize());
        vo.setChunkOverlap(entity.getChunkOverlap());
        vo.setChunkStrategy(entity.getChunkStrategy());
        vo.setRetrievalTopK(entity.getRetrievalTopK());
        vo.setSimilarityThreshold(entity.getSimilarityThreshold());
        vo.setDocumentCount(entity.getDocumentCount());
        vo.setChunkCount(entity.getChunkCount());
        vo.setVectorCount(entity.getVectorCount());
        vo.setTotalSizeBytes(entity.getTotalSizeBytes());
        vo.setStatus(entity.getStatus());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        vo.setCreatedBy(entity.getCreatedBy());
        vo.setUpdatedBy(entity.getUpdatedBy());
        return vo;
    }
}
