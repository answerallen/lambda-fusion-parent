package com.lambda.fusion.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.model.entity.McpServerEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * MCP Server Mapper 接口
 *
 * @author Jin
 */
@Mapper
public interface McpServerMapper extends BaseMapper<McpServerEntity> {

    /**
     * 查询所有启用的 MCP Server 列表
     *
     * @return 启用的 MCP 服务器列表
     */
    default List<McpServerEntity> selectEnabled() {
        return selectList(new LambdaQueryWrapper<McpServerEntity>().eq(McpServerEntity::getEnabled, true));
    }

    /**
     * 根据租户ID查询 MCP Server 列表
     *
     * @param tenantId 租户ID
     * @return MCP 服务器列表
     */
    default List<McpServerEntity> selectByTenantId(String tenantId) {
        return selectList(new LambdaQueryWrapper<McpServerEntity>()
                .eq(McpServerEntity::getTenantId, tenantId)
                .orderByDesc(McpServerEntity::getCreatedAt));
    }

    /**
     * 根据名称查询 MCP Server（用于唯一性检查）
     *
     * @param name 服务器名称
     * @return MCP 服务器实体，不存在返回 null
     */
    default McpServerEntity selectByName(String name) {
        return selectOne(new LambdaQueryWrapper<McpServerEntity>()
                .eq(McpServerEntity::getName, name)
                .last("limit 1"));
    }
}
