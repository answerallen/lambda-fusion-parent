package com.lambda.fusion.ai.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lambda.fusion.ai.workflow.model.entity.WorkflowExecutionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WorkflowExecutionMapper extends BaseMapper<WorkflowExecutionEntity> {

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("progress") Integer progress);

    int updateCompletion(
            @Param("id") Long id,
            @Param("status") String status,
            @Param("outputResult") String outputResult,
            @Param("executionLog") String executionLog,
            @Param("completedAt") java.time.LocalDateTime completedAt,
            @Param("durationMs") Integer durationMs);

    int updateFailure(
            @Param("id") Long id,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("errorStack") String errorStack,
            @Param("completedAt") java.time.LocalDateTime completedAt);
}
