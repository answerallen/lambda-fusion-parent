package com.lambda.fusion.ai.agent.evaluator;

import com.lambda.fusion.ai.agent.AgentState;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 兼容纯 JavaScript 的复杂编排条件引擎。
 * 基于 JSR 223 脚本控制解析前端传输的 JS 段落逻辑。
 */
@Slf4j
@Component
public class JavaScriptConditionEvaluator implements ConditionEvaluator {

    private final ScriptEngineManager manager = new ScriptEngineManager();

    @Override
    public boolean evaluate(String expression, AgentState state) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }

        // 尝试获取 JDK 的 JS 引擎 (在高版本 JDK 中可能缺乏 nashorn，可引入 graal.js)
        ScriptEngine engine = manager.getEngineByName("JavaScript");
        if (engine == null) {
            engine = manager.getEngineByName("nashorn");
        }

        if (engine == null) {
            log.error("JavaScriptConditionEvaluator: 未能找到有效的 JavaScript/Nashorn 脚本执行引擎，将阻止流转。");
            return false;
        }

        try {
            Bindings bindings = engine.createBindings();
            // 直接透出给 JS 脚本操作：例如 state.getAttributes().get('param')
            bindings.put("state", state);

            // 封装常用的 JS 布尔变量
            if (state.getPendingToolRequests() != null) {
                bindings.put("hasTools", !state.getPendingToolRequests().isEmpty());
            }

            if (state.getAttributes() != null) {
                state.getAttributes().forEach(bindings::put);
            }

            Object result = engine.eval(expression, bindings);
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else if (result != null) {
                // 如果 JS 脚本未强制返回 Boolean，这里可以看作 truthy，但是为了安全性，建议只认可 Boolean
                return Boolean.parseBoolean(result.toString());
            }
            return false;

        } catch (ScriptException e) {
            log.error("JavaScriptConditionEvaluator 运行错误: {}", expression, e);
            return false;
        }
    }

    @Override
    public String getType() {
        return "js";
    }
}
