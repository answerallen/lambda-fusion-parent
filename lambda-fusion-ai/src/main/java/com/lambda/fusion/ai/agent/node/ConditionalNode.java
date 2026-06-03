package com.lambda.fusion.ai.agent.node;

import com.lambda.fusion.ai.agent.AgentNode;
import com.lambda.fusion.ai.agent.AgentState;
import com.lambda.fusion.ai.agent.evaluator.ConditionEvaluator;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 条件分支节点。
 * 根据条件表达式的结果决定执行哪个分支。
 * <p>
 * 配置属性：
 * - branches: 分支配置数组，每个分支包含 condition（条件表达式）和 target（目标节点ID）
 * - defaultTarget: 默认分支目标节点ID（当所有条件都不满足时）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConditionalNode implements AgentNode {

    private final Map<String, ConditionEvaluator> evaluators;

    @Override
    public String getName() {
        return "CONDITIONAL";
    }

    @Override
    public ExecutionResult execute(AgentState state) {
        // 从 state 中获取节点配置
        Map<String, Object> properties = state.getCurrentNodeProperties();

        if (properties == null) {
            log.warn("条件节点缺少配置属性");
            return new ExecutionResult(state, null);
        }

        // 获取分支配置
        Object branchesObj = properties.get("branches");
        if (!(branchesObj instanceof java.util.List<?> branchList)) {
            log.warn("条件节点缺少分支配置");
            return new ExecutionResult(state, null);
        }

        // 评估每个分支的条件
        for (Object branchObj : branchList) {
            if (!(branchObj instanceof Map<?, ?> branch)) {
                continue;
            }

            String condition = (String) branch.get("condition");
            String conditionType = (String) branch.get("conditionType");
            String target = (String) branch.get("target");

            if (target == null || target.isEmpty()) {
                log.warn("条件节点分支配置了空的目标节点，已跳过。condition: {}, conditionType: {}", condition, conditionType);
                continue;
            }

            // 空条件视为直通
            if (condition == null || condition.isEmpty()) {
                log.debug("条件节点分支无条件，直通到: {}", target);
                return new ExecutionResult(state, target);
            }

            // 获取对应的 evaluator
            ConditionEvaluator evaluator = evaluators.get(conditionType);
            if (evaluator == null) {
                log.warn("未找到条件评估器类型: {}", conditionType);
                continue;
            }

            // 评估条件
            boolean matched = evaluator.evaluate(condition, state);
            log.debug("条件节点评估: type={}, condition={}, matched={}", conditionType, condition, matched);

            if (matched) {
                return new ExecutionResult(state, target);
            }
        }

        // 所有条件都不满足，使用默认分支
        String defaultTarget = (String) properties.get("defaultTarget");
        if (defaultTarget != null && !defaultTarget.isEmpty()) {
            log.debug("条件节点使用默认分支: {}", defaultTarget);
            return new ExecutionResult(state, defaultTarget);
        }

        log.warn("条件节点没有匹配任何分支，且无默认分支");
        return new ExecutionResult(state, null);
    }
}
