package com.lambda.fusion.ai.commons.agent.evaluator;

import com.lambda.fusion.ai.commons.agent.AgentState;
import java.util.HashSet;
import java.util.Set;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 兼容纯 JavaScript 的复杂编排条件引擎。
 * 基于 JSR 223 脚本控制解析前端传输的 JS 段落逻辑。
 * <p>
 * 安全设计：
 * - 对表达式进行白名单验证，禁止危险操作
 * - 限制可访问的对象和方法
 * - 添加表达式长度限制
 * - 捕获并记录所有异常
 */
@Slf4j
@Component
public class JavaScriptConditionEvaluator implements ConditionEvaluator {

    private final ScriptEngineManager manager = new ScriptEngineManager();

    private volatile String cachedEngineName;

    private static final int MAX_EXPRESSION_LENGTH = 1000;

    private static final Set<String> DANGEROUS_KEYWORDS = new HashSet<>();

    static {
        DANGEROUS_KEYWORDS.add("java.lang.Runtime");
        DANGEROUS_KEYWORDS.add("java.lang.ProcessBuilder");
        DANGEROUS_KEYWORDS.add("java.io.File");
        DANGEROUS_KEYWORDS.add("java.net.Socket");
        DANGEROUS_KEYWORDS.add("java.sql.DriverManager");
        DANGEROUS_KEYWORDS.add("require");
        DANGEROUS_KEYWORDS.add("load");
        DANGEROUS_KEYWORDS.add("eval");
        DANGEROUS_KEYWORDS.add("exec");
        DANGEROUS_KEYWORDS.add("system");
    }

    private ScriptEngine getScriptEngine() {
        if (cachedEngineName == null) {
            synchronized (this) {
                if (cachedEngineName == null) {
                    cachedEngineName = resolveScriptEngineName();
                }
            }
        }
        if (cachedEngineName == null) {
            return null;
        }
        return manager.getEngineByName(cachedEngineName);
    }

    private String resolveScriptEngineName() {
        ScriptEngine engine;

        engine = manager.getEngineByName("graal.js");
        if (engine != null) {
            log.info("使用 GraalJS 脚本引擎");
            return "graal.js";
        }

        engine = manager.getEngineByName("nashorn");
        if (engine != null) {
            log.info("使用 Nashorn 脚本引擎");
            return "nashorn";
        }

        engine = manager.getEngineByName("JavaScript");
        if (engine != null) {
            log.info("使用 JavaScript 脚本引擎");
            return "JavaScript";
        }

        log.error("未找到可用的 JavaScript 脚本引擎。请添加依赖：\n"
                + "JDK 15+: 添加 org.graalvm.polyglot:polyglot 和 org.graalvm.js:js 依赖\n" + "JDK 8-14: Nashorn 已内置");
        return null;
    }

    @Override
    public boolean evaluate(String expression, AgentState state) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }

        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            log.warn("JavaScript 表达式超过最大长度限制: {} > {}", expression.length(), MAX_EXPRESSION_LENGTH);
            return false;
        }

        if (!isExpressionSafe(expression)) {
            log.warn("检测到不安全的 JavaScript 表达式，已拒绝执行");
            return false;
        }

        ScriptEngine engine = getScriptEngine();
        if (engine == null) {
            log.error("JavaScriptConditionEvaluator: 未能找到有效的 JavaScript 脚本执行引擎，将阻止流转。");
            return false;
        }

        try {
            Bindings bindings = engine.createBindings();

            bindings.put("state", state);
            bindings.put("attributes", state.getAttributes());
            bindings.put("attrs", state.getAttributes());
            bindings.put("currentNodeId", state.getCurrentNodeId());
            bindings.put("currentNodeType", state.getCurrentNodeType());
            bindings.put("currentNodeProperties", state.getCurrentNodeProperties());
            bindings.put("nodeProps", state.getCurrentNodeProperties());
            bindings.put("graphNodeProperties", state.getGraphNodeProperties());
            bindings.put("graphProps", state.getGraphNodeProperties());
            bindings.put("sessionId", state.getSessionId());
            bindings.put("kbId", state.getKbId());
            bindings.put("llmModelId", state.getLlmModelId());

            if (state.getPendingToolRequests() != null) {
                bindings.put("hasTools", !state.getPendingToolRequests().isEmpty());
            } else {
                bindings.put("hasTools", false);
            }

            if (state.getAttributes() != null) {
                state.getAttributes().forEach((key, value) -> {
                    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                        bindings.put(key, value);
                    }
                });
            }

            Object result = engine.eval(expression, bindings);
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else if (result != null) {
                return Boolean.parseBoolean(result.toString());
            }
            return false;

        } catch (ScriptException e) {
            log.error("JavaScriptConditionEvaluator 运行错误: {}", expression, e);
            return false;
        } catch (Exception e) {
            log.error("JavaScriptConditionEvaluator 未预期的异常: {}", expression, e);
            return false;
        }
    }

    private boolean isExpressionSafe(String expression) {
        if (expression == null) {
            return true;
        }

        String expr = expression.toLowerCase();

        for (String keyword : DANGEROUS_KEYWORDS) {
            if (expr.contains(keyword.toLowerCase())) {
                return false;
            }
        }

        String[] dangerousPatterns = {
            "java.",
            "function",
            "constructor",
            "prototype",
            "eval(",
            "exec(",
            "system(",
            "process",
            "child_process",
            "require(",
            "import(",
            "load(",
            "readfile",
            "writefile"
        };

        for (String pattern : dangerousPatterns) {
            if (expr.contains(pattern)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String getType() {
        return "js";
    }
}
