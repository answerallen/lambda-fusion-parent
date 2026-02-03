package com.lambda.fusion.ai.util;

import org.springframework.stereotype.Component;

/**
 * 向量表名解析工具
 *
 * @author Jin
 */
@Component
public class VectorTableNameResolver {

    /**
     * 根据向量维度获取对应的向量表名
     *
     * @param dimension 向量维度
     * @return 向量表名，格式: ai_vector_store_{dimension}
     */
    public String resolve(Integer dimension) {
        if (dimension == null || dimension <= 0) {
            throw new IllegalArgumentException("向量维度必须大于0");
        }
        return "ai_vector_store_" + dimension;
    }

    /**
     * 从表名解析出向量维度
     *
     * @param tableName 表名
     * @return 向量维度
     */
    public Integer parseDimension(String tableName) {
        if (tableName == null || !tableName.startsWith("ai_vector_store_")) {
            throw new IllegalArgumentException("无效的向量表名: " + tableName);
        }
        String dimensionStr = tableName.substring("ai_vector_store_".length());
        try {
            return Integer.parseInt(dimensionStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法解析向量维度: " + tableName, e);
        }
    }
}
