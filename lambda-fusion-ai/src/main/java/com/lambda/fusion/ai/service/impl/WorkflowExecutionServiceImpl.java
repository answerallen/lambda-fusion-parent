package com.lambda.fusion.ai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambda.fusion.ai.commons.agent.AgentGraph;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.agent.factory.AgentGraphFactory;
import com.lambda.fusion.ai.commons.agent.model.GraphDefinition;
import com.lambda.fusion.ai.commons.agent.model.NodeDefinition;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.PipelineExecutionMapper;
import com.lambda.fusion.ai.mapper.WorkflowMapper;
import com.lambda.fusion.ai.model.WorkflowExecutionRequest;
import com.lambda.fusion.ai.model.WorkflowExecutionResult;
import com.lambda.fusion.ai.model.entity.PipelineExecutionEntity;
import com.lambda.fusion.ai.model.entity.WorkflowEntity;
import com.lambda.fusion.ai.service.WorkflowExecutionService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    private final WorkflowMapper workflowMapper;
    private final PipelineExecutionMapper executionMapper;
    private final AgentGraphFactory agentGraphFactory;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionResult execute(Long workflowId, WorkflowExecutionRequest request) {
        log.info("开始执行工作流, workflowId={}", workflowId);

        WorkflowEntity workflow = loadWorkflow(workflowId);
        PipelineExecutionEntity execution = createExecutionRecord(workflow, request);

        try {
            AgentGraph graph = agentGraphFactory.buildFromDefinition(workflow.getGraphJson());
            AgentState state = prepareInitialState(request, execution);

            long startTime = System.currentTimeMillis();
            state = graph.invoke(state);
            long duration = System.currentTimeMillis() - startTime;

            WorkflowExecutionResult result = extractResult(state, execution, duration);
            updateExecutionSuccess(execution, result);

            log.info("工作流执行完成, executionId={}, duration={}ms", execution.getExecutionId(), duration);
            return result;

        } catch (Exception e) {
            log.error("工作流执行失败, workflowId={}", workflowId, e);
            updateExecutionFailure(execution, e);
            throw new AiBusinessException(AiErrorCode.WORKFLOW_EXECUTION_FAILED, e);
        }
    }

    @Override
    public void executeStream(Long workflowId, WorkflowExecutionRequest request, StreamingChatResponseHandler handler) {
        log.info("开始流式执行工作流, workflowId={}", workflowId);

        WorkflowEntity workflow = loadWorkflow(workflowId);
        PipelineExecutionEntity execution = createExecutionRecord(workflow, request);

        try {
            AgentGraph graph = agentGraphFactory.buildFromDefinition(workflow.getGraphJson());
            AgentState state = prepareInitialState(request, execution);

            Long modelId = request.getLlmModelId();
            if (modelId == null) {
                modelId = resolveDefaultModelId(workflow);
            }
            if (modelId != null) {
                state.setLlmModelId(modelId);
            }
            state.getAttributes().put("streamHandler", handler);

            long startTime = System.currentTimeMillis();
            state = graph.invoke(state);
            long duration = System.currentTimeMillis() - startTime;

            WorkflowExecutionResult result = extractResult(state, execution, duration);
            updateExecutionSuccess(execution, result);
            ChatResponse chatResponse = buildChatResponse(state);
            handler.onCompleteResponse(chatResponse);

        } catch (Exception e) {
            log.error("流式执行工作流失败, workflowId={}", workflowId, e);
            updateExecutionFailure(execution, e);
            handler.onError(e);
            throw new AiBusinessException(AiErrorCode.WORKFLOW_EXECUTION_FAILED, e);
        }
    }

    @Override
    public WorkflowExecutionResult getExecutionResult(String executionId) {
        if (!StringUtils.hasText(executionId)) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "执行ID不能为空");
        }

        PipelineExecutionEntity entity = executionMapper.selectByExecutionId(executionId);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_EXECUTION_NOT_FOUND, "执行记录不存在: " + executionId);
        }

        return entityToResult(entity);
    }

    @Override
    public Page<WorkflowExecutionResult> listExecutions(Long workflowId, int pageNum, int pageSize) {
        Page<PipelineExecutionEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<PipelineExecutionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PipelineExecutionEntity::getPipelineId, workflowId)
                .orderByDesc(PipelineExecutionEntity::getCreatedAt);

        Page<PipelineExecutionEntity> entityPage = executionMapper.selectPage(page, wrapper);

        Page<WorkflowExecutionResult> resultPage = new Page<>(pageNum, pageSize, entityPage.getTotal());
        resultPage.setRecords(
                entityPage.getRecords().stream().map(this::entityToResult).toList());

        return resultPage;
    }

    private WorkflowEntity loadWorkflow(Long workflowId) {
        if (workflowId == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_NOT_FOUND, "工作流ID不能为空");
        }

        WorkflowEntity workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw AiBusinessException.workflowNotFound(workflowId);
        }

        return workflow;
    }

    private PipelineExecutionEntity createExecutionRecord(WorkflowEntity workflow, WorkflowExecutionRequest request) {
        PipelineExecutionEntity execution = new PipelineExecutionEntity();
        execution.setExecutionId(IdUtil.fastSimpleUUID());
        execution.setPipelineId(workflow.getId());
        execution.setPipelineVersion(1);
        execution.setUserId(request.getUserId());
        execution.setTenantId(request.getTenantId());
        execution.setStatus("RUNNING");
        execution.setProgress(0);
        execution.setStartedAt(LocalDateTime.now());

        try {
            if (request.getInputParams() != null) {
                execution.setInputParams(objectMapper.writeValueAsString(request.getInputParams()));
            }
        } catch (Exception e) {
            log.warn("序列化输入参数失败", e);
        }

        executionMapper.insert(execution);
        return execution;
    }

    private AgentState prepareInitialState(WorkflowExecutionRequest request, PipelineExecutionEntity execution) {
        AgentState state = new AgentState();
        state.setSessionId(request.getSessionId());
        state.setKbId(request.getKbId());
        state.setLlmModelId(request.getLlmModelId());

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("executionId", execution.getExecutionId());
        attributes.put("userId", request.getUserId());
        attributes.put("tenantId", request.getTenantId());
        attributes.put(AgentGraph.TRACE_ENABLED_ATTRIBUTE, request.getTraceEnabled());
        state.setAttributes(attributes);

        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            state.setMessages(new ArrayList<>(request.getMessages()));
        }

        return state;
    }

    private WorkflowExecutionResult extractResult(AgentState state, PipelineExecutionEntity execution, long duration) {
        WorkflowExecutionResult.WorkflowExecutionResultBuilder builder = WorkflowExecutionResult.builder()
                .executionId(execution.getExecutionId())
                .finished(state.isFinished())
                .durationMs(duration)
                .executionTrace(state.getExecutionTrace())
                .executionStats(state.getExecutionStats());

        if (!state.getMessages().isEmpty()) {
            ChatMessage lastMsg = state.getMessages().getLast();
            if (lastMsg instanceof AiMessage aiMsg) {
                builder.answer(aiMsg.text());
            }
        }

        int promptTokens = (Integer) state.getAttributes().getOrDefault("promptTokens", 0);
        int completionTokens = (Integer) state.getAttributes().getOrDefault("completionTokens", 0);
        builder.promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens);

        builder.status("COMPLETED");

        return builder.build();
    }

    private void updateExecutionSuccess(PipelineExecutionEntity execution, WorkflowExecutionResult result) {
        try {
            execution.setStatus("COMPLETED");
            execution.setProgress(100);
            execution.setCompletedAt(LocalDateTime.now());
            execution.setDurationMs(
                    result.getDurationMs() != null ? result.getDurationMs().intValue() : 0);

            Map<String, Object> outputResult = new LinkedHashMap<>();
            outputResult.put("answer", result.getAnswer());
            outputResult.put("promptTokens", result.getPromptTokens());
            outputResult.put("completionTokens", result.getCompletionTokens());
            outputResult.put("totalTokens", result.getTotalTokens());
            execution.setOutputResult(objectMapper.writeValueAsString(outputResult));

            if (result.getExecutionTrace() != null) {
                Map<String, Object> log = new LinkedHashMap<>();
                log.put("trace", result.getExecutionTrace());
                log.put("stats", result.getExecutionStats());
                execution.setExecutionLog(objectMapper.writeValueAsString(log));
            }

            executionMapper.updateById(execution);
        } catch (Exception e) {
            log.error("更新执行记录失败", e);
        }
    }

    private void updateExecutionFailure(PipelineExecutionEntity execution, Throwable error) {
        try {
            execution.setStatus("FAILED");
            execution.setCompletedAt(LocalDateTime.now());
            execution.setErrorCode("EXECUTION_ERROR");
            execution.setErrorMessage(error.getMessage());

            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            execution.setErrorStack(sw.toString());

            executionMapper.updateById(execution);
        } catch (Exception e) {
            log.error("更新执行失败记录失败", e);
        }
    }

    private Long resolveDefaultModelId(WorkflowEntity workflow) {
        if (workflow == null || !StringUtils.hasText(workflow.getGraphJson())) {
            return null;
        }
        try {
            GraphDefinition definition = objectMapper.readValue(workflow.getGraphJson(), GraphDefinition.class);
            if (definition == null
                    || definition.getNodes() == null
                    || definition.getNodes().isEmpty()) {
                return null;
            }
            for (NodeDefinition node : definition.getNodes()) {
                if (node == null
                        || node.getProperties() == null
                        || node.getProperties().isEmpty()) {
                    continue;
                }
                Long resolvedModelId = resolveModelId(node.getProperties());
                if (resolvedModelId != null) {
                    return resolvedModelId;
                }
            }
            return null;
        } catch (Exception e) {
            log.warn("解析工作流默认模型失败, workflowId={}", workflow.getId(), e);
            return null;
        }
    }

    private Long resolveModelId(Map<String, Object> properties) {
        Object modelIdValue = properties.get("llmModelId");
        if (modelIdValue == null) {
            modelIdValue = properties.get("modelId");
        }
        if (modelIdValue instanceof Number numberValue) {
            long modelId = numberValue.longValue();
            return modelId > 0 ? modelId : null;
        }
        if (modelIdValue instanceof String textValue && StringUtils.hasText(textValue)) {
            try {
                long modelId = Long.parseLong(textValue.trim());
                return modelId > 0 ? modelId : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private WorkflowExecutionResult entityToResult(PipelineExecutionEntity entity) {
        return WorkflowExecutionResult.builder()
                .executionId(entity.getExecutionId())
                .status(entity.getStatus())
                .durationMs(
                        entity.getDurationMs() != null ? entity.getDurationMs().longValue() : null)
                .errorMessage(entity.getErrorMessage())
                .build();
    }

    private ChatResponse buildChatResponse(AgentState state) {
        AiMessage aiMessage = null;
        if (state != null && state.getMessages() != null && !state.getMessages().isEmpty()) {
            ChatMessage lastMessage = state.getMessages().getLast();
            if (lastMessage instanceof AiMessage parsedMessage) {
                aiMessage = parsedMessage;
            }
        }
        int promptTokens = state != null ? (Integer) state.getAttributes().getOrDefault("promptTokens", 0) : 0;
        int completionTokens = state != null ? (Integer) state.getAttributes().getOrDefault("completionTokens", 0) : 0;
        return ChatResponse.builder()
                .aiMessage(aiMessage != null ? aiMessage : AiMessage.from(""))
                .tokenUsage(new TokenUsage(promptTokens, completionTokens))
                .finishReason(FinishReason.STOP)
                .build();
    }
}
