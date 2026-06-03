package com.lambda.fusion.ai.agent.evaluator;

import com.lambda.fusion.ai.agent.AgentState;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
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

    private static final Pattern SAFE_VARIABLE_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");

    private static final String[] EXECUTION_CONTEXT_KEYS = {
        "executionId",
        "userId",
        "tenantId",
        "username",
        "orgId",
        "roles",
        "isAdmin",
        "isDev",
        "isManager",
        "isTenantManager",
        "isAnyManager"
    };

    private static final int MAX_EXPRESSION_LENGTH = 1000;

    private static final Set<String> DANGEROUS_KEYWORDS = new HashSet<>();

    private static final Set<String> RESERVED_BINDING_NAMES = Set.of(
            "attributes",
            "attrs",
            "currentNodeId",
            "currentNodeType",
            "currentNodeProperties",
            "nodeProps",
            "graphNodeProperties",
            "graphProps",
            "sessionId",
            "kbId",
            "llmModelId",
            "hasTools",
            "executionId",
            "userId",
            "tenantId",
            "username",
            "orgId",
            "roles",
            "isAdmin",
            "isDev",
            "isManager",
            "isTenantManager",
            "isAnyManager",
            "constructor",
            "prototype",
            "__proto__");

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
        return resolveScriptEngine();
    }

    private ScriptEngine resolveScriptEngine() {
        ScriptEngine engine = tryCreateGraalJsEngine();
        if (engine != null) {
            return engine;
        }

        log.error("所有 JavaScript 引擎均不可用");
        return null;
    }

    private ScriptEngine tryCreateGraalJsEngine() {
        try {
            log.info("正在通过显式构造器创建 GraalJS 引擎");

            HostAccess restrictedAccess = HostAccess.newBuilder(HostAccess.NONE)
                    .allowAccessAnnotatedBy(HostAccess.Export.class)
                    .allowListAccess(true)
                    .allowMapAccess(true)
                    .build();

            Context.Builder contextBuilder =
                    Context.newBuilder("js").allowHostAccess(restrictedAccess).allowHostClassLookup(s -> false);

            Class<?> graalJsClass = Class.forName("com.oracle.truffle.js.scriptengine.GraalJSScriptEngine");

            Method createMethod = graalJsClass.getMethod(
                    "create", Class.forName("org.graalvm.polyglot.Engine"), contextBuilder.getClass());

            return (ScriptEngine) createMethod.invoke(null, null, contextBuilder);
        } catch (ClassNotFoundException e) {
            log.debug("GraalJSScriptEngine 类未找到");
        } catch (NoClassDefFoundError e) {
            log.warn("GraalJSScriptEngine 类初始化失败: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("无法创建 GraalJS 引擎: {}", e.getMessage());
        }
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

            bindings.put("attributes", state.getAttributes());
            bindings.put("attrs", state.getAttributes());
            bindings.put("state", new SafeStateView(state));
            bindings.put("currentNodeId", state.getCurrentNodeId());
            bindings.put("currentNodeType", state.getCurrentNodeType());
            bindings.put("currentNodeProperties", state.getCurrentNodeProperties());
            bindings.put("nodeProps", state.getCurrentNodeProperties());
            bindings.put("graphNodeProperties", state.getGraphNodeProperties());
            bindings.put("graphProps", state.getGraphNodeProperties());
            bindings.put("sessionId", state.getSessionId());
            bindings.put("kbId", state.getKbId());
            bindings.put("llmModelId", state.getLlmModelId());
            injectExecutionContextVariables(bindings, state);

            if (state.getPendingToolRequests() != null) {
                bindings.put("hasTools", !state.getPendingToolRequests().isEmpty());
            } else {
                bindings.put("hasTools", false);
            }

            injectSafeAttributeVariables(bindings, state);

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

    public static final class SafeStateView {
        private final AgentState delegate;

        public SafeStateView(AgentState delegate) {
            this.delegate = delegate;
        }

        @HostAccess.Export
        public String getSessionId() {
            return delegate.getSessionId();
        }

        @HostAccess.Export
        public String getKbId() {
            return delegate.getKbId();
        }

        @HostAccess.Export
        public String getLlmModelId() {
            return delegate.getLlmModelId();
        }

        @HostAccess.Export
        public String getCurrentNodeId() {
            return delegate.getCurrentNodeId();
        }

        @HostAccess.Export
        public String getCurrentNodeType() {
            return delegate.getCurrentNodeType();
        }
    }

    private void injectSafeAttributeVariables(Bindings bindings, AgentState state) {
        if (state.getAttributes() == null || state.getAttributes().isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : state.getAttributes().entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            if (!SAFE_VARIABLE_NAME.matcher(key).matches()) {
                continue;
            }
            if (RESERVED_BINDING_NAMES.contains(key)) {
                continue;
            }
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (!isAllowedBindingValue(value)) {
                continue;
            }
            bindings.put(key, value);
        }
    }

    private boolean isAllowedBindingValue(Object value) {
        if (value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character) {
            return true;
        }
        if (value instanceof Map<?, ?>
                || value instanceof Iterable<?>
                || value.getClass().isArray()) {
            return true;
        }
        return false;
    }

    private void injectExecutionContextVariables(Bindings bindings, AgentState state) {
        if (state.getAttributes() == null) {
            return;
        }
        for (String key : EXECUTION_CONTEXT_KEYS) {
            bindings.put(key, state.getAttributes().get(key));
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
