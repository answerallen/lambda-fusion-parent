package com.lambda.fusion.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.ai.commons.agent.AgentGraph;
import com.lambda.fusion.ai.commons.agent.AgentState;
import com.lambda.fusion.ai.commons.agent.factory.AgentGraphBuildOptions;
import com.lambda.fusion.ai.commons.agent.factory.AgentGraphFactory;
import com.lambda.fusion.ai.commons.agent.model.GraphDefinition;
import com.lambda.fusion.ai.commons.agent.model.NodeDefinition;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.exception.AiErrorCode;
import com.lambda.fusion.ai.commons.utils.AgentUtils;
import com.lambda.fusion.ai.commons.utils.CostCalculator;
import com.lambda.fusion.ai.mapper.LlmModelMapper;
import com.lambda.fusion.ai.mapper.WorkflowExecutionMapper;
import com.lambda.fusion.ai.mapper.WorkflowMapper;
import com.lambda.fusion.ai.model.WorkflowExecutionRequest;
import com.lambda.fusion.ai.model.WorkflowExecutionResult;
import com.lambda.fusion.ai.model.WorkflowExecutionStatus;
import com.lambda.fusion.ai.model.WorkflowResumeRequest;
import com.lambda.fusion.ai.model.entity.LlmModelEntity;
import com.lambda.fusion.ai.model.entity.WorkflowEntity;
import com.lambda.fusion.ai.model.entity.WorkflowExecutionEntity;
import com.lambda.fusion.ai.service.AtomicSessionUpdateService;
import com.lambda.fusion.ai.service.WorkflowExecutionService;
import com.lambda.fusion.core.FusionConstants;
import com.lambda.fusion.core.identity.UserDetails;
import com.lambda.fusion.core.utils.AuthUtils;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl implements WorkflowExecutionService {

    static final String ATTR_USER_ID = "userId";
    static final String ATTR_TENANT_ID = "tenantId";
    static final String ATTR_USERNAME = "username";
    static final String ATTR_ORG_ID = "orgId";
    static final String ATTR_ROLES = "roles";
    static final String ATTR_IS_ADMIN = "isAdmin";
    static final String ATTR_IS_DEV = "isDev";
    static final String ATTR_IS_MANAGER = "isManager";
    static final String ATTR_IS_TENANT_MANAGER = "isTenantManager";
    static final String ATTR_IS_ANY_MANAGER = "isAnyManager";
    static final String ATTR_THREAD_ID = "threadId";
    static final String ATTR_INPUT_PARAMS = "inputParams";
    static final String STATUS_WAITING_FOR_INPUT = "WAITING_FOR_INPUT";

    private final WorkflowMapper workflowMapper;
    private final WorkflowExecutionMapper executionMapper;
    private final AgentGraphFactory agentGraphFactory;
    private final ObjectMapper objectMapper;
    private final AtomicSessionUpdateService atomicSessionUpdateService;
    private final CostCalculator costCalculator;
    private final LlmModelMapper llmModelMapper;
    private final ObjectProvider<MemorySaver> checkpointSaverProvider;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionResult execute(String workflowId, WorkflowExecutionRequest request) {
        log.info("开始执行工作流, workflowId={}", workflowId);

        WorkflowEntity workflow = loadWorkflow(workflowId);
        WorkflowExecutionEntity execution = createExecutionRecord(workflow, request);

        try {
            String threadId = resolveThreadId(request, execution.getId());
            AgentGraph graph =
                    agentGraphFactory.buildFromDefinition(workflow.getGraphJson(), buildGraphBuildOptions(request));
            AgentState state = prepareInitialState(request, execution, threadId);
            RunnableConfig runnableConfig = buildRunnableConfig(threadId);

            long startTime = System.currentTimeMillis();
            state = graph.invokeOptional(state, runnableConfig).orElse(state);
            long duration = System.currentTimeMillis() - startTime;
            Optional<StateSnapshot<AgentGraph.LangGraphRuntimeState>> snapshot = graph.stateSnapshotOf(runnableConfig);

            WorkflowExecutionResult result = mapToExecutionResult(state, execution, threadId, snapshot, duration);
            updateExecutionSuccess(execution, result);

            if (Boolean.TRUE.equals(result.getFinished())) {
                settleAfterExecution(request, result, "workflow-sync");
            }

            log.info(
                    "工作流执行完成, executionId={}, status={}, duration={}ms",
                    execution.getId(),
                    result.getStatus(),
                    duration);
            return result;

        } catch (Exception e) {
            log.error("工作流执行失败, workflowId={}", workflowId, e);
            updateExecutionFailure(execution, e);
            throw new AiBusinessException(AiErrorCode.WORKFLOW_EXECUTION_FAILED, e);
        }
    }

    @Override
    public void executeStream(
            String workflowId, WorkflowExecutionRequest request, StreamingChatResponseHandler handler) {
        log.info("寮€濮嬫祦寮忔墽琛屽伐浣滄祦, workflowId={}", workflowId);

        WorkflowEntity workflow = loadWorkflow(workflowId);
        WorkflowExecutionEntity execution = createExecutionRecord(workflow, request);

        try {
            String threadId = resolveThreadId(request, execution.getId());
            AgentGraph graph =
                    agentGraphFactory.buildFromDefinition(workflow.getGraphJson(), buildGraphBuildOptions(request));
            AgentState state = prepareInitialState(request, execution, threadId);
            RunnableConfig runnableConfig = buildRunnableConfig(threadId);

            String modelId = request.getLlmModelId();
            if (modelId == null) {
                modelId = resolveDefaultModelId(workflow);
            }
            if (modelId != null) {
                state.setLlmModelId(modelId);
            }
            // 使用幂等包装器防止重复触发回调
            StreamingChatResponseHandler idempotentHandler = new IdempotentStreamingHandler(handler);
            state.getAttributes().put("streamHandler", idempotentHandler);

            long startTime = System.currentTimeMillis();
            state = executeGraphStream(graph, state, runnableConfig);
            long duration = System.currentTimeMillis() - startTime;
            Optional<StateSnapshot<AgentGraph.LangGraphRuntimeState>> snapshot = graph.stateSnapshotOf(runnableConfig);

            WorkflowExecutionResult result = mapToExecutionResult(state, execution, threadId, snapshot, duration);
            updateExecutionSuccess(execution, result);
            ChatResponse chatResponse = buildChatResponse(state);
            idempotentHandler.onCompleteResponse(chatResponse);

            if (Boolean.TRUE.equals(result.getFinished())) {
                settleAfterExecution(request, result, "workflow-stream");
            }

        } catch (Exception e) {
            log.error("流式执行工作流失败, workflowId={}", workflowId, e);
            updateExecutionFailure(execution, e);
            handler.onError(e);
            throw new AiBusinessException(AiErrorCode.WORKFLOW_EXECUTION_FAILED, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionResult resume(String workflowId, WorkflowResumeRequest request) {
        Assert.notNull(request, "WorkflowResumeRequest 不能为空！");
        log.info("恢复执行工作流, workflowId={}, threadId={}", workflowId, request.getThreadId());
        String threadId = requireThreadId(request.getThreadId());
        WorkflowEntity workflow = loadWorkflow(workflowId);
        WorkflowExecutionEntity execution = createResumeExecutionRecord(workflow, request);

        try {
            AgentGraph graph =
                    agentGraphFactory.buildFromDefinition(workflow.getGraphJson(), buildGraphBuildOptions(request));
            RunnableConfig lookupConfig = buildRunnableConfig(threadId, request.getCheckpointId(), null);
            StateSnapshot<AgentGraph.LangGraphRuntimeState> snapshot =
                    requireStateSnapshot(graph, lookupConfig, threadId, request.getCheckpointId());
            Map<String, Object> stateUpdate =
                    buildResumeStateUpdate(snapshot.state().agentState(), request, execution.getId(), threadId);
            RunnableConfig resumeConfig = buildRunnableConfig(
                    threadId, snapshot.config().checkPointId().orElse(null), snapshot.next());

            long startTime = System.currentTimeMillis();
            AgentState state = graph.resumeOptional(stateUpdate, resumeConfig)
                    .orElse(snapshot.state().agentState());
            long duration = System.currentTimeMillis() - startTime;
            Optional<StateSnapshot<AgentGraph.LangGraphRuntimeState>> latestSnapshot =
                    graph.stateSnapshotOf(buildRunnableConfig(threadId));

            WorkflowExecutionResult result = mapToExecutionResult(state, execution, threadId, latestSnapshot, duration);
            updateExecutionSuccess(execution, result);

            if (Boolean.TRUE.equals(result.getFinished())) {
                settleAfterExecution(toExecutionRequest(request, threadId), result, "workflow-resume");
            }
            return result;
        } catch (AiBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("恢复工作流执行失败, workflowId={}, threadId={}", workflowId, threadId, e);
            updateExecutionFailure(execution, e);
            throw new AiBusinessException(AiErrorCode.WORKFLOW_EXECUTION_FAILED, e);
        }
    }

    /**
     * 幂等流式响应处理器包装器
     * 防止 onCompleteResponse 被重复调用
     */
    private static class IdempotentStreamingHandler implements StreamingChatResponseHandler {
        private final StreamingChatResponseHandler delegate;
        private volatile boolean completed = false;

        IdempotentStreamingHandler(StreamingChatResponseHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onPartialResponse(String token) {
            delegate.onPartialResponse(token);
        }

        @Override
        public void onCompleteResponse(ChatResponse response) {
            if (completed) {
                log.warn("onCompleteResponse 已被调用过，忽略重复调用");
                return;
            }
            synchronized (this) {
                if (completed) {
                    log.warn("onCompleteResponse 已被调用过，忽略重复调用");
                    return;
                }
                completed = true;
                delegate.onCompleteResponse(response);
            }
        }

        @Override
        public void onError(Throwable error) {
            delegate.onError(error);
        }
    }

    @Override
    public WorkflowExecutionResult getExecutionResult(String executionId) {
        if (!StringUtils.hasText(executionId)) {
            throw new AiBusinessException(AiErrorCode.INVALID_PARAMETER, "执行ID不能为空");
        }

        WorkflowExecutionEntity entity = executionMapper.selectById(executionId);
        if (entity == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_EXECUTION_NOT_FOUND, "执行记录不存在: " + executionId);
        }

        return entityToResult(entity);
    }

    @Override
    public WorkflowExecutionStatus getExecutionStatus(String workflowId, String threadId, String checkpointId) {
        WorkflowEntity workflow = loadWorkflow(workflowId);
        String normalizedThreadId = requireThreadId(threadId);
        try {
            AgentGraph graph =
                    agentGraphFactory.buildFromDefinition(workflow.getGraphJson(), buildCheckpointGraphBuildOptions());
            StateSnapshot<AgentGraph.LangGraphRuntimeState> snapshot = requireStateSnapshot(
                    graph,
                    buildRunnableConfig(normalizedThreadId, checkpointId, null),
                    normalizedThreadId,
                    checkpointId);
            return mapToExecutionStatus(snapshot, normalizedThreadId);
        } catch (AiBusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询工作流执行状态失败, workflowId={}, threadId={}", workflowId, normalizedThreadId, e);
            throw new AiBusinessException(AiErrorCode.WORKFLOW_EXECUTION_FAILED, e);
        }
    }

    @Override
    public Page<WorkflowExecutionResult> listExecutions(String workflowId, int pageNum, int pageSize) {
        Page<WorkflowExecutionEntity> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<WorkflowExecutionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowExecutionEntity::getPipelineId, workflowId)
                .orderByDesc(WorkflowExecutionEntity::getCreatedAt);

        Page<WorkflowExecutionEntity> entityPage = executionMapper.selectPage(page, wrapper);

        Page<WorkflowExecutionResult> resultPage = new Page<>(pageNum, pageSize, entityPage.getTotal());
        resultPage.setRecords(
                entityPage.getRecords().stream().map(this::entityToResult).toList());

        return resultPage;
    }

    private WorkflowEntity loadWorkflow(String workflowId) {
        if (workflowId == null) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_NOT_FOUND, "工作流ID不能为空");
        }

        WorkflowEntity workflow = workflowMapper.selectById(workflowId);
        if (workflow == null) {
            throw AiBusinessException.workflowNotFound(workflowId);
        }
        validateWorkflowAccess(workflow, workflowId);

        return workflow;
    }

    private void validateWorkflowAccess(WorkflowEntity workflow, String workflowId) {
        String currentTenantId = AuthUtils.getTenantId();
        if (!StringUtils.hasText(currentTenantId)) {
            return;
        }
        if (StringUtils.hasText(workflow.getTenantId()) && !currentTenantId.equals(workflow.getTenantId())) {
            throw AiBusinessException.workflowNotFound(workflowId);
        }
    }

    private WorkflowExecutionEntity createExecutionRecord(WorkflowEntity workflow, WorkflowExecutionRequest request) {
        UserDetails currentUser = getCurrentUserSafely();
        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setPipelineId(workflow.getId());
        execution.setPipelineVersion(1);
        execution.setUserId(resolveUserId(request, currentUser));
        execution.setTenantId(resolveTenantId(request, currentUser));
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

    private WorkflowExecutionEntity createResumeExecutionRecord(
            WorkflowEntity workflow, WorkflowResumeRequest request) {
        UserDetails currentUser = getCurrentUserSafely();
        WorkflowExecutionEntity execution = new WorkflowExecutionEntity();
        execution.setPipelineId(workflow.getId());
        execution.setPipelineVersion(1);
        execution.setUserId(currentUser == null ? null : currentUser.getName());
        execution.setTenantId(currentUser == null ? null : currentUser.getTenantId());
        execution.setStatus("RUNNING");
        execution.setProgress(0);
        execution.setStartedAt(LocalDateTime.now());

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("threadId", request.getThreadId());
            payload.put("checkpointId", request.getCheckpointId());
            payload.put("message", request.getMessage());
            payload.put("inputParams", request.getInputParams());
            execution.setInputParams(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("序列化恢复执行输入参数失败", e);
        }

        executionMapper.insert(execution);
        return execution;
    }

    private AgentState prepareInitialState(
            WorkflowExecutionRequest request, WorkflowExecutionEntity execution, String threadId) {
        AgentState state = new AgentState();
        state.setSessionId(request.getSessionId());
        state.setKbId(request.getKbId());
        state.setLlmModelId(request.getLlmModelId());

        UserDetails currentUser = getCurrentUserSafely();
        state.setAttributes(buildExecutionContextAttributes(request, execution.getId(), threadId, currentUser));

        if (request.getMessages() != null && !request.getMessages().isEmpty()) {
            state.setMessages(new ArrayList<>(request.getMessages()));
        }

        return state;
    }

    Map<String, Object> buildExecutionContextAttributes(
            WorkflowExecutionRequest request, String executionId, String threadId, UserDetails currentUser) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("executionId", executionId);
        attributes.put(ATTR_USER_ID, resolveUserId(request, currentUser));
        attributes.put(ATTR_TENANT_ID, resolveTenantId(request, currentUser));
        attributes.put(AgentGraph.TRACE_ENABLED_ATTRIBUTE, request.getTraceEnabled());
        if (StringUtils.hasText(threadId)) {
            attributes.put(ATTR_THREAD_ID, threadId);
        }

        if (currentUser != null) {
            Set<String> roles = currentUser.getRoles();
            attributes.put(ATTR_USERNAME, currentUser.getUsername());
            attributes.put(ATTR_ORG_ID, currentUser.getOrgId());
            attributes.put(ATTR_ROLES, new ArrayList<>(roles));
            attributes.put(ATTR_IS_ADMIN, roles.contains(FusionConstants.ROLE_ADMIN));
            attributes.put(ATTR_IS_DEV, roles.contains(FusionConstants.ROLE_DEV));
            attributes.put(ATTR_IS_MANAGER, roles.contains(FusionConstants.ROLE_MANAGER));
            attributes.put(
                    ATTR_IS_TENANT_MANAGER,
                    roles.stream().anyMatch(role -> role.startsWith(FusionConstants.ROLE_TENANT + FusionConstants.AT)));
            attributes.put(
                    ATTR_IS_ANY_MANAGER,
                    roles.stream()
                            .anyMatch(role -> role.equals(FusionConstants.ROLE_DEV)
                                    || role.equals(FusionConstants.ROLE_ADMIN)
                                    || role.startsWith(FusionConstants.ROLE_TENANT + FusionConstants.AT)));
        } else {
            attributes.put(ATTR_ROLES, Collections.emptyList());
            attributes.put(ATTR_IS_ADMIN, false);
            attributes.put(ATTR_IS_DEV, false);
            attributes.put(ATTR_IS_MANAGER, false);
            attributes.put(ATTR_IS_TENANT_MANAGER, false);
            attributes.put(ATTR_IS_ANY_MANAGER, false);
        }

        return attributes;
    }

    AgentGraphBuildOptions buildGraphBuildOptions(WorkflowExecutionRequest request) {
        if (request == null) {
            return null;
        }
        return buildGraphBuildOptions(buildCompileConfig(request), request.getMaxIterations());
    }

    AgentGraphBuildOptions buildGraphBuildOptions(WorkflowResumeRequest request) {
        if (request == null) {
            return buildCheckpointGraphBuildOptions();
        }
        return buildGraphBuildOptions(buildCompileConfig(request), request.getMaxIterations());
    }

    AgentGraphBuildOptions buildCheckpointGraphBuildOptions() {
        return buildGraphBuildOptions(buildCompileConfig(true, null, null, null), null);
    }

    private AgentGraphBuildOptions buildGraphBuildOptions(CompileConfig compileConfig, Integer maxIterations) {
        if (compileConfig == null && maxIterations == null) {
            return null;
        }
        AgentGraphBuildOptions options = new AgentGraphBuildOptions();
        options.setCompileConfig(compileConfig);
        options.setMaxIterations(maxIterations);
        return options;
    }

    CompileConfig buildCompileConfig(WorkflowExecutionRequest request) {
        if (request == null) {
            return null;
        }
        return buildCompileConfig(
                Boolean.TRUE.equals(request.getCheckpointEnabled()),
                request.getInterruptBefore(),
                request.getInterruptAfter(),
                request.getReleaseThread());
    }

    CompileConfig buildCompileConfig(WorkflowResumeRequest request) {
        return buildCompileConfig(
                true,
                request == null ? null : request.getInterruptBefore(),
                request == null ? null : request.getInterruptAfter(),
                request == null ? null : request.getReleaseThread());
    }

    private CompileConfig buildCompileConfig(
            boolean checkpointEnabled, String interruptBefore, String interruptAfter, Boolean releaseThread) {
        String normalizedInterruptBefore = normalizeText(interruptBefore);
        String normalizedInterruptAfter = normalizeText(interruptAfter);
        if (!checkpointEnabled
                && !StringUtils.hasText(normalizedInterruptBefore)
                && !StringUtils.hasText(normalizedInterruptAfter)
                && releaseThread == null) {
            return null;
        }
        CompileConfig.Builder builder = CompileConfig.builder();
        if (checkpointEnabled) {
            builder.checkpointSaver(resolveCheckpointSaver());
        }
        if (StringUtils.hasText(normalizedInterruptBefore)) {
            builder.interruptBefore(normalizedInterruptBefore);
        }
        if (StringUtils.hasText(normalizedInterruptAfter)) {
            builder.interruptAfter(normalizedInterruptAfter);
        }
        if (releaseThread != null) {
            builder.releaseThread(releaseThread);
        }
        return builder.build();
    }

    private MemorySaver resolveCheckpointSaver() {
        MemorySaver checkpointSaver = checkpointSaverProvider.getIfAvailable();
        if (checkpointSaver == null) {
            throw new AiBusinessException(AiErrorCode.CONFIGURATION_ERROR, "未配置 LangGraph4j checkpoint saver");
        }
        return checkpointSaver;
    }

    RunnableConfig buildRunnableConfig(String threadId) {
        return buildRunnableConfig(threadId, null, null);
    }

    RunnableConfig buildRunnableConfig(String threadId, String checkpointId, String nextNode) {
        if (!StringUtils.hasText(threadId)) {
            return null;
        }
        RunnableConfig.Builder builder = RunnableConfig.builder().threadId(threadId);
        if (StringUtils.hasText(checkpointId)) {
            builder.checkPointId(checkpointId);
        }
        if (StringUtils.hasText(nextNode)) {
            builder.nextNode(nextNode);
        }
        return builder.build();
    }

    String resolveThreadId(WorkflowExecutionRequest request, String fallbackThreadId) {
        if (request == null) {
            return null;
        }
        String requestThreadId = normalizeText(request.getThreadId());
        if (StringUtils.hasText(requestThreadId)) {
            return requestThreadId;
        }
        boolean requiresThreadAffinity = Boolean.TRUE.equals(request.getCheckpointEnabled())
                || StringUtils.hasText(normalizeText(request.getInterruptBefore()))
                || StringUtils.hasText(normalizeText(request.getInterruptAfter()));
        if (!requiresThreadAffinity) {
            return null;
        }
        String sessionId = normalizeText(request.getSessionId());
        return StringUtils.hasText(sessionId) ? sessionId : normalizeText(fallbackThreadId);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    String requireThreadId(String threadId) {
        String normalizedThreadId = normalizeText(threadId);
        if (!StringUtils.hasText(normalizedThreadId)) {
            throw new AiBusinessException(AiErrorCode.WORKFLOW_THREAD_ID_REQUIRED, "工作流线程ID不能为空");
        }
        return normalizedThreadId;
    }

    StateSnapshot<AgentGraph.LangGraphRuntimeState> requireStateSnapshot(
            AgentGraph graph, RunnableConfig runnableConfig, String threadId, String checkpointId) {
        return graph.stateSnapshotOf(runnableConfig)
                .orElseThrow(() -> new AiBusinessException(
                        AiErrorCode.WORKFLOW_CHECKPOINT_NOT_FOUND,
                        String.format(
                                "未找到工作流checkpoint, threadId=%s, checkpointId=%s",
                                threadId, normalizeText(checkpointId))));
    }

    WorkflowExecutionRequest toExecutionRequest(WorkflowResumeRequest request, String threadId) {
        WorkflowExecutionRequest executionRequest = new WorkflowExecutionRequest();
        executionRequest.setThreadId(threadId);
        executionRequest.setSessionId(request.getSessionId());
        executionRequest.setKbId(request.getKbId());
        executionRequest.setLlmModelId(request.getLlmModelId());
        executionRequest.setTraceEnabled(request.getTraceEnabled());
        executionRequest.setCalledFromChat(false);
        return executionRequest;
    }

    Map<String, Object> buildResumeStateUpdate(
            AgentState snapshotState, WorkflowResumeRequest request, String executionId, String threadId) {
        Map<String, Object> stateUpdate = new LinkedHashMap<>();

        if (StringUtils.hasText(request.getSessionId())) {
            stateUpdate.put(AgentGraph.LangGraphRuntimeState.SESSION_ID_KEY, request.getSessionId());
        }
        if (StringUtils.hasText(request.getKbId())) {
            stateUpdate.put(AgentGraph.LangGraphRuntimeState.KB_ID_KEY, request.getKbId());
        }
        if (StringUtils.hasText(request.getLlmModelId())) {
            stateUpdate.put(AgentGraph.LangGraphRuntimeState.LLM_MODEL_ID_KEY, request.getLlmModelId());
        }
        if (StringUtils.hasText(request.getMessage())) {
            stateUpdate.put(
                    AgentGraph.LangGraphRuntimeState.MESSAGES_KEY,
                    List.of(UserMessage.from(request.getMessage().trim())));
        }

        Map<String, Object> attributes =
                snapshotState == null ? new LinkedHashMap<>() : new LinkedHashMap<>(snapshotState.getAttributes());
        attributes.put("executionId", executionId);
        attributes.put(ATTR_THREAD_ID, threadId);
        attributes.put(AgentGraph.TRACE_ENABLED_ATTRIBUTE, request.getTraceEnabled());
        if (request.getInputParams() != null && !request.getInputParams().isEmpty()) {
            attributes.put(ATTR_INPUT_PARAMS, new LinkedHashMap<>(request.getInputParams()));
        }
        stateUpdate.put(AgentGraph.LangGraphRuntimeState.ATTRIBUTES_KEY, attributes);
        return stateUpdate;
    }

    private String resolveUserId(WorkflowExecutionRequest request, UserDetails currentUser) {
        if (StringUtils.hasText(request.getUserId())) {
            return request.getUserId();
        }
        return currentUser == null ? null : currentUser.getName();
    }

    private String resolveTenantId(WorkflowExecutionRequest request, UserDetails currentUser) {
        if (StringUtils.hasText(request.getTenantId())) {
            return request.getTenantId();
        }
        return currentUser == null ? null : currentUser.getTenantId();
    }

    private UserDetails getCurrentUserSafely() {
        try {
            return AuthUtils.getUser();
        } catch (Exception e) {
            log.debug("当前线程未获取到登录主体信息: {}", e.getMessage());
            return null;
        }
    }

    private WorkflowExecutionResult mapToExecutionResult(
            AgentState state,
            WorkflowExecutionEntity execution,
            String threadId,
            Optional<StateSnapshot<AgentGraph.LangGraphRuntimeState>> snapshot,
            long duration) {
        String nextNode =
                snapshot.map(StateSnapshot::next).map(this::normalizeText).orElse(null);
        String checkpointId = snapshot.flatMap(item -> item.config().checkPointId())
                .map(this::normalizeText)
                .orElse(null);
        boolean interrupted = !state.isFinished() && StringUtils.hasText(nextNode);
        String status = state.isFinished() ? "COMPLETED" : interrupted ? STATUS_WAITING_FOR_INPUT : "RUNNING";

        WorkflowExecutionResult.WorkflowExecutionResultBuilder builder = WorkflowExecutionResult.builder()
                .id(execution.getId())
                .threadId(threadId)
                .checkpointId(checkpointId)
                .nextNode(nextNode)
                .interrupted(interrupted)
                .finished(state.isFinished())
                .status(status)
                .durationMs(duration)
                .executionTrace(state.getExecutionTrace())
                .executionStats(state.getExecutionStats());

        if (!state.getMessages().isEmpty()) {
            ChatMessage lastMsg = state.getMessages().getLast();
            if (lastMsg instanceof AiMessage aiMsg) {
                builder.answer(aiMsg.text());
            }
        }

        int promptTokens = AgentUtils.asInt(state.getAttributes().get("promptTokens"));
        int completionTokens = AgentUtils.asInt(state.getAttributes().get("completionTokens"));
        builder.promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens);

        return builder.build();
    }

    private void updateExecutionSuccess(WorkflowExecutionEntity execution, WorkflowExecutionResult result) {
        try {
            execution.setStatus(result.getStatus());
            execution.setProgress(Boolean.TRUE.equals(result.getFinished()) ? 100 : 90);
            execution.setCurrentStep(result.getNextNode());
            if (Boolean.TRUE.equals(result.getFinished())) {
                execution.setCompletedAt(LocalDateTime.now());
            }
            execution.setDurationMs(
                    result.getDurationMs() != null ? result.getDurationMs().intValue() : 0);

            Map<String, Object> outputResult = new LinkedHashMap<>();
            outputResult.put("answer", result.getAnswer());
            outputResult.put("threadId", result.getThreadId());
            outputResult.put("checkpointId", result.getCheckpointId());
            outputResult.put("nextNode", result.getNextNode());
            outputResult.put("interrupted", result.getInterrupted());
            outputResult.put("finished", result.getFinished());
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

    private void updateExecutionFailure(WorkflowExecutionEntity execution, Throwable error) {
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

    private String resolveDefaultModelId(WorkflowEntity workflow) {
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

            String llmProcessorModelId = null;
            String fallbackModelId = null;

            for (NodeDefinition node : definition.getNodes()) {
                if (node == null
                        || node.getProperties() == null
                        || node.getProperties().isEmpty()) {
                    continue;
                }
                String resolvedModelId = resolveModelId(node.getProperties());
                if (resolvedModelId != null) {
                    if ("LLM_PROCESSOR".equals(node.getType()) && llmProcessorModelId == null) {
                        llmProcessorModelId = resolvedModelId;
                        log.debug("从 LLM_PROCESSOR 节点 {} 找到模型ID: {}", node.getId(), resolvedModelId);
                    } else if (fallbackModelId == null) {
                        fallbackModelId = resolvedModelId;
                        log.debug("从节点 {} 找到模型ID: {}", node.getId(), resolvedModelId);
                    }
                }
            }

            String result = llmProcessorModelId != null ? llmProcessorModelId : fallbackModelId;
            if (result != null) {
                log.info("工作流 {} 使用默认模型ID: {}", workflow.getId(), result);
            }
            return result;
        } catch (Exception e) {
            log.warn("解析工作流默认模型失败, workflowId={}", workflow.getId(), e);
            return null;
        }
    }

    private String resolveModelId(Map<String, Object> properties) {
        Object modelIdValue = properties.get("llmModelId");
        if (modelIdValue == null) {
            modelIdValue = properties.get("modelId");
        }
        if (modelIdValue instanceof Number numberValue) {
            return numberValue.toString();
        }
        if (modelIdValue instanceof String textValue && StringUtils.hasText(textValue)) {
            try {
                return textValue;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private AgentState executeGraphStream(AgentGraph graph, AgentState state, RunnableConfig runnableConfig) {
        NodeOutput<AgentGraph.LangGraphRuntimeState> finalOutput = graph.stream(state, runnableConfig)
                .<NodeOutput<AgentGraph.LangGraphRuntimeState>>reduce(null, (acc, output) -> output)
                .join();
        if (finalOutput == null || finalOutput.state() == null) {
            return state;
        }
        return finalOutput.state().agentState();
    }

    private WorkflowExecutionStatus mapToExecutionStatus(
            StateSnapshot<AgentGraph.LangGraphRuntimeState> snapshot, String threadId) {
        AgentState state = snapshot.state().agentState();
        String nextNode = normalizeText(snapshot.next());
        boolean finished = state.isFinished();
        boolean interrupted = !finished && StringUtils.hasText(nextNode);
        String status = finished ? "COMPLETED" : interrupted ? STATUS_WAITING_FOR_INPUT : "RUNNING";

        return WorkflowExecutionStatus.builder()
                .threadId(threadId)
                .checkpointId(snapshot.config().checkPointId().orElse(null))
                .executionId(asText(state.getAttributes().get("executionId")))
                .currentNodeId(asText(state.getAttributes().get(AgentGraph.CURRENT_NODE_ID_ATTRIBUTE)))
                .nextNode(nextNode)
                .status(status)
                .finished(finished)
                .interrupted(interrupted)
                .waitingForInput(interrupted)
                .answer(extractAnswer(state))
                .executionTrace(state.getExecutionTrace())
                .executionStats(state.getExecutionStats())
                .build();
    }

    private WorkflowExecutionResult entityToResult(WorkflowExecutionEntity entity) {
        Map<String, Object> outputResult = parseJsonObject(entity.getOutputResult());
        return WorkflowExecutionResult.builder()
                .id(entity.getId())
                .threadId(asText(outputResult.get("threadId")))
                .checkpointId(asText(outputResult.get("checkpointId")))
                .nextNode(asText(outputResult.get("nextNode")))
                .interrupted(Boolean.TRUE.equals(outputResult.get("interrupted")))
                .finished(Boolean.TRUE.equals(outputResult.get("finished")) || "COMPLETED".equals(entity.getStatus()))
                .answer(asText(outputResult.get("answer")))
                .status(entity.getStatus())
                .durationMs(
                        entity.getDurationMs() != null ? entity.getDurationMs().longValue() : null)
                .errorMessage(entity.getErrorMessage())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonObject(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.debug("解析JSON对象失败: {}", e.getMessage());
            return Map.of();
        }
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return normalizeText(text);
        }
        return String.valueOf(value);
    }

    private String extractAnswer(AgentState state) {
        if (state == null || state.getMessages() == null || state.getMessages().isEmpty()) {
            return null;
        }
        ChatMessage lastMessage = state.getMessages().getLast();
        if (lastMessage instanceof AiMessage aiMessage) {
            return aiMessage.text();
        }
        return null;
    }

    /**
     * 工作流执行完成后进行结算（仅在独立入口调用时执行）。
     * <p>当 {@link WorkflowExecutionRequest#getCalledFromChat()} 为 {@code true} 时，
     * 表明由聊天消息层({@code ChatMessageServiceImpl.persistStreamMessages})负责结算，
     * 本方法将跳过以避免双重计费。</p>
     *
     * @param request  工作流执行请求信息
     * @param result   执行结果
     * @param scene    场景标识（用于日志区分同步/流式）
     */
    private void settleAfterExecution(WorkflowExecutionRequest request, WorkflowExecutionResult result, String scene) {
        // 聊天触发的工作流跳过，由消息层统一结算
        if (Boolean.TRUE.equals(request.getCalledFromChat())) {
            log.debug("[ACCOUNTING] scene={} 聊天触发模式，跳过工作流结算", scene);
            return;
        }

        String sessionId = request.getSessionId();
        String llmModelId = request.getLlmModelId();
        int totalTokens = result.getTotalTokens() != null ? result.getTotalTokens() : 0;
        BigDecimal cost = calculateWorkflowCost(llmModelId, result);

        // 更新会话统计（若有 sessionId）
        if (StringUtils.hasText(sessionId)) {
            try {
                // 工作流仅一条 AI 回复，不计入用户消息数
                atomicSessionUpdateService.updateSessionStatistics(sessionId, 1, totalTokens, cost);
                log.info(
                        "[ACCOUNTING] scene={} sessionId={} modelId={} tokens={} cost={}",
                        scene,
                        sessionId,
                        llmModelId,
                        totalTokens,
                        cost);
            } catch (Exception e) {
                log.warn("[ACCOUNTING] 更新会话统计失败 scene={} sessionId={}: {}", scene, sessionId, e.getMessage());
            }
        }

        // 原子更新模型统计（若有 llmModelId）
        if (StringUtils.hasText(llmModelId)) {
            try {
                BigDecimal safeCost = cost != null ? cost : BigDecimal.ZERO;
                int rows = llmModelMapper.atomicUpdateStatistics(llmModelId, totalTokens, safeCost);
                if (rows == 0) {
                    log.warn("[ACCOUNTING] 模型统计更新失败，模型不存在 scene={} modelId={}", scene, llmModelId);
                } else {
                    log.debug(
                            "[ACCOUNTING] 模型统计原子更新成功 scene={} modelId={} +tokens={} +cost={}",
                            scene,
                            llmModelId,
                            totalTokens,
                            safeCost);
                }
            } catch (Exception e) {
                log.warn("[ACCOUNTING] 更新模型统计失败 scene={} modelId={}: {}", scene, llmModelId, e.getMessage());
            }
        }
    }

    /**
     * 计算工作流执行成本。
     * 若模型信息不存在或价格未配置，返回 ZERO。
     */
    private BigDecimal calculateWorkflowCost(String llmModelId, WorkflowExecutionResult result) {
        if (!StringUtils.hasText(llmModelId)) {
            return BigDecimal.ZERO;
        }
        try {
            LlmModelEntity model = llmModelMapper.selectById(llmModelId);
            if (model == null || model.getInputTokenPrice() == null || model.getOutputTokenPrice() == null) {
                return BigDecimal.ZERO;
            }
            int promptTokens = result.getPromptTokens() != null ? result.getPromptTokens() : 0;
            int completionTokens = result.getCompletionTokens() != null ? result.getCompletionTokens() : 0;
            var costResult = costCalculator.calculateCost(
                    promptTokens, completionTokens, model.getInputTokenPrice(), model.getOutputTokenPrice());
            return costResult.getTotalCost();
        } catch (Exception e) {
            log.warn("计算工作流成本失败, modelId={}: {}", llmModelId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    private ChatResponse buildChatResponse(AgentState state) {
        AiMessage aiMessage = null;
        if (state != null && state.getMessages() != null && !state.getMessages().isEmpty()) {
            ChatMessage lastMessage = state.getMessages().getLast();
            if (lastMessage instanceof AiMessage parsedMessage) {
                aiMessage = parsedMessage;
            }
        }
        int promptTokens = state != null && state.getAttributes() != null
                ? AgentUtils.asInt(state.getAttributes().get("promptTokens"))
                : 0;
        int completionTokens = state != null && state.getAttributes() != null
                ? AgentUtils.asInt(state.getAttributes().get("completionTokens"))
                : 0;
        return ChatResponse.builder()
                .aiMessage(aiMessage != null ? aiMessage : AiMessage.from(""))
                .tokenUsage(new TokenUsage(promptTokens, completionTokens))
                .finishReason(FinishReason.STOP)
                .build();
    }
}
