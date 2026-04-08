package com.lambda.fusion.ai.commons.support.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * 批量插入工具类
 * 提供分批处理大数据集的功能
 *
 * @author Jin
 */
@Slf4j
public class ListPartitionUtils {

    /**
     * 默认批次大小
     */
    public static final int DEFAULT_BATCH_SIZE = 200;

    /**
     * 最大批次大小
     */
    public static final int MAX_BATCH_SIZE = 500;

    /**
     * 最小批次大小
     */
    public static final int MIN_BATCH_SIZE = 50;

    /**
     * 分批处理列表数据
     *
     * @param dataList   原始数据列表
     * @param batchSize  批次大小
     * @param batchConsumer 批次处理器
     * @param <T>        数据类型
     */
    public static <T> void batchProcess(List<T> dataList, int batchSize, Consumer<List<T>> batchConsumer) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }

        // 验证并调整批次大小
        int validatedBatchSize = validateBatchSize(batchSize);
        int totalSize = dataList.size();
        int batchCount = (int) Math.ceil((double) totalSize / validatedBatchSize);

        log.debug("开始分批处理，总数据量: {}，批次大小: {}，预计批次: {}", totalSize, validatedBatchSize, batchCount);

        for (int i = 0; i < batchCount; i++) {
            int start = i * validatedBatchSize;
            int end = Math.min(start + validatedBatchSize, totalSize);
            List<T> batch = dataList.subList(start, end);

            try {
                batchConsumer.accept(batch);
                log.debug("批次 {}/{} 处理完成，大小: {}", i + 1, batchCount, batch.size());
            } catch (Exception e) {
                log.error("批次 {}/{} 处理失败，数据范围: {}-{}", i + 1, batchCount, start, end, e);
                throw new RuntimeException("批量处理失败，批次: " + (i + 1) + "/" + batchCount, e);
            }
        }

        log.debug("分批处理完成，共处理 {} 条数据", totalSize);
    }

    /**
     * 使用默认批次大小分批处理
     *
     * @param dataList      原始数据列表
     * @param batchConsumer 批次处理器
     * @param <T>           数据类型
     */
    public static <T> void batchProcess(List<T> dataList, Consumer<List<T>> batchConsumer) {
        batchProcess(dataList, DEFAULT_BATCH_SIZE, batchConsumer);
    }

    /**
     * 验证批次大小
     *
     * @param batchSize 输入的批次大小
     * @return 验证后的批次大小
     */
    public static int validateBatchSize(int batchSize) {
        if (batchSize < MIN_BATCH_SIZE) {
            log.warn("批次大小 {} 小于最小值 {}，使用最小值", batchSize, MIN_BATCH_SIZE);
            return MIN_BATCH_SIZE;
        }
        if (batchSize > MAX_BATCH_SIZE) {
            log.warn("批次大小 {} 超过最大值 {}，使用最大值", batchSize, MAX_BATCH_SIZE);
            return MAX_BATCH_SIZE;
        }
        return batchSize;
    }

    /**
     * 将列表分割成多个批次
     *
     * @param dataList  原始数据列表
     * @param batchSize 批次大小
     * @param <T>       数据类型
     * @return 批次列表
     */
    public static <T> List<List<T>> splitIntoBatches(List<T> dataList, int batchSize) {
        if (dataList == null || dataList.isEmpty()) {
            return new ArrayList<>();
        }

        int validatedBatchSize = validateBatchSize(batchSize);
        List<List<T>> batches = new ArrayList<>();
        int totalSize = dataList.size();

        for (int i = 0; i < totalSize; i += validatedBatchSize) {
            int end = Math.min(i + validatedBatchSize, totalSize);
            batches.add(new ArrayList<>(dataList.subList(i, end)));
        }

        return batches;
    }

    /**
     * 使用默认批次大小分割列表
     *
     * @param dataList 原始数据列表
     * @param <T>      数据类型
     * @return 批次列表
     */
    public static <T> List<List<T>> splitIntoBatches(List<T> dataList) {
        return splitIntoBatches(dataList, DEFAULT_BATCH_SIZE);
    }
}
