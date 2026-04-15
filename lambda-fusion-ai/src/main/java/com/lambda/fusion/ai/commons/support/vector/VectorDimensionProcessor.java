package com.lambda.fusion.ai.commons.support.vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 向量维度处理服务
 * 处理不同维度向量的填充、截断和归一化
 *
 * @author Jin
 */
@Slf4j
@Service
public class VectorDimensionProcessor {

    /**
     * 最大支持的向量维度
     * 与数据库表 ai_vector_store.embedding 列的维度一致
     */
    public static final int MAX_DIMENSION = 4096;

    /**
     * 默认向量维度
     */
    public static final int DEFAULT_DIMENSION = 1536;

    /**
     * 支持的向量维度列表
     */
    public static final List<Integer> SUPPORTED_DIMENSIONS = List.of(768, 1536, 3072, 4096);

    /**
     * 将向量归一化到标准维度（填充或截断）
     * <p>
     * 如果向量维度小于 MAX_DIMENSION，则填充零到 MAX_DIMENSION
     * 如果向量维度大于 MAX_DIMENSION，则截断到 MAX_DIMENSION
     *
     * @param vector 原始向量
     * @return 归一化后的向量，维度为 MAX_DIMENSION
     */
    public List<Double> normalizeToMaxDimension(List<Double> vector) {
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("向量不能为空");
        }

        int originalDimension = vector.size();

        // 如果已经是最大维度，直接返回
        if (originalDimension == MAX_DIMENSION) {
            return new ArrayList<>(vector);
        }

        List<Double> result = new ArrayList<>(MAX_DIMENSION);

        if (originalDimension < MAX_DIMENSION) {
            // 填充零
            result.addAll(vector);
            for (int i = originalDimension; i < MAX_DIMENSION; i++) {
                result.add(0.0);
            }
            log.debug("向量维度从 {} 填充到 {}", originalDimension, MAX_DIMENSION);
        } else {
            // 截断
            result.addAll(vector.subList(0, MAX_DIMENSION));
            log.warn("向量维度 {} 超过最大值 {}，已截断", originalDimension, MAX_DIMENSION);
        }

        return result;
    }

    /**
     * 将向量归一化到指定维度
     *
     * @param vector          原始向量
     * @param targetDimension 目标维度
     * @return 归一化后的向量
     */
    public List<Double> normalizeToDimension(List<Double> vector, int targetDimension) {
        if (vector == null || vector.isEmpty()) {
            throw new IllegalArgumentException("向量不能为空");
        }

        if (targetDimension <= 0 || targetDimension > MAX_DIMENSION) {
            throw new IllegalArgumentException("目标维度必须在 1-" + MAX_DIMENSION + " 之间，当前: " + targetDimension);
        }

        int originalDimension = vector.size();

        if (originalDimension == targetDimension) {
            return new ArrayList<>(vector);
        }

        List<Double> result = new ArrayList<>(targetDimension);

        if (originalDimension < targetDimension) {
            result.addAll(vector);
            for (int i = originalDimension; i < targetDimension; i++) {
                result.add(0.0);
            }
        } else {
            result.addAll(vector.subList(0, targetDimension));
        }

        return result;
    }

    /**
     * 验证向量维度是否支持
     *
     * @param dimension 维度
     * @return 是否支持
     */
    public boolean isDimensionSupported(int dimension) {
        return dimension > 0 && dimension <= MAX_DIMENSION;
    }

    /**
     * 获取可容纳当前向量的支持维度。
     * <p>
     * 优先选择不小于原始维度的最小支持维度，避免无谓截断。
     *
     * @param dimension 原始维度
     * @return 向上兼容后的支持维度
     */
    public int getNearestSupportedDimension(int dimension) {
        if (dimension <= 0) {
            return DEFAULT_DIMENSION;
        }

        if (dimension > MAX_DIMENSION) {
            return MAX_DIMENSION;
        }

        for (int supportedDim : SUPPORTED_DIMENSIONS) {
            if (dimension <= supportedDim) {
                return supportedDim;
            }
        }

        return MAX_DIMENSION;
    }

    /**
     * 获取实际存储维度
     * <p>
     * 根据原始维度返回应该存储的维度（用于记录 dimension 字段）
     *
     * @param originalDimension 原始向量维度
     * @return 实际存储维度
     */
    public int getActualDimension(int originalDimension) {
        if (originalDimension <= 0) {
            return DEFAULT_DIMENSION;
        }
        return Math.min(originalDimension, MAX_DIMENSION);
    }

    /**
     * 批量归一化向量
     *
     * @param vectors 原始向量列表
     * @return 归一化后的向量列表
     */
    public List<List<Double>> normalizeBatch(List<List<Double>> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return Collections.emptyList();
        }

        List<List<Double>> result = new ArrayList<>(vectors.size());
        for (List<Double> vector : vectors) {
            result.add(normalizeToMaxDimension(vector));
        }
        return result;
    }
}
