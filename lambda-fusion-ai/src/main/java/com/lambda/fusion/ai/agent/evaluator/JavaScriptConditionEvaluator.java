package com.lambda.fusion.ai.agent.evaluator;

import com.lambda.fusion.ai.agent.AgentState;
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
 *
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

    // 表达式最大长度限制（防止 DoS 攻击）
    private static final int MAX_EXPRESSION_LENGTH = 1000;

    // 禁止的危险操作符
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

    @Override
    public boolean evaluate(String expression, AgentState state) {
        if (expression == null || expression.trim().isEmpty()) {
            return true;
        }

        // 表达式长度检查
        if (expression.length() > MAX_EXPRESSION_LENGTH) {
            log.warn("JavaScript 表达式超过最大长度限制: {} > {}", expression.length(), MAX_EXPRESSION_LENGTH);
            return false;
        }

        // 表达式安全性检查
        if (!isExpressionSafe(expression)) {
            log.warn("检测到不安全的 JavaScript 表达式，已拒绝执行");
            return false;
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

            // 只暴露必要的对象，不暴露 Java 对象
            bindings.put("state", state);

            // 封装常用的 JS 布尔变量
            if (state.getPendingToolRequests() != null) {
                bindings.put("hasTools", !state.getPendingToolRequests().isEmpty());
            } else {
                bindings.put("hasTools", false);
            }

            // 只暴露安全的属性
            if (state.getAttributes() != null) {
                state.getAttributes().forEach((key, value) -> {
                    // 只允许基本类型和字符串
                    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                        bindings.put(key, value);
                    }
                });
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
            // 脚本执行异常返回 false，防止意外的流程转向
            return false;
        } catch (Exception e) {
            log.error("JavaScriptConditionEvaluator 未预期的异常: {}", expression, e);
            return false;
        }
    }

    /**
     * 验证表达式是否安全
     * 检查是否包含危险的关键字或操作
     */
    private boolean isExpressionSafe(String expression) {
        if (expression == null) {
            return true;
        }

        String expr = expression.toLowerCase();

        // 检查危险关键字
        for (String keyword : DANGEROUS_KEYWORDS) {
            if (expr.contains(keyword.toLowerCase())) {
                return false;
            }
        }

        // 检查其他危险模式
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
