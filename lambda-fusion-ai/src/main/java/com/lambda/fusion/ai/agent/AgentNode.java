package com.lambda.fusion.ai.agent;

/**
 * Agent工作流图的执行节点抽象接口
 * <p>
 * <strong>无状态约束</strong>：所有实现类必须保持无状态。
 * 同一类型的多个节点实例将共享同一个 Spring Bean，
 * 节点属性通过 {@link AgentState#getCurrentNodeProperties()} 获取。
 * <p>
 * <strong>禁止</strong>在实现类中引入实例级别的可变状态（如非 final 字段、缓存等），
 * 否则会导致并发问题和数据污染。
 * <p>
 * <strong>正确示例</strong>：
 * <pre>
 * public class MyNode implements AgentNode {
 *     private final SomeService service;  // OK: final 依赖注入
 *
 *     public ExecutionResult execute(AgentState state) {
 *         Map&lt;String, Object&gt; props = state.getCurrentNodeProperties();  // OK: 从 state 获取属性
 *         // ...
 *     }
 * }
 * </pre>
 * <p>
 * <strong>错误示例</strong>：
 * <pre>
 * public class BadNode implements AgentNode {
 *     private Map&lt;String, Object&gt; cache;  // 错误: 可变状态，多节点共享时会污染
 *
 *     public ExecutionResult execute(AgentState state) {
 *         cache.put(...);  // 错误: 修改共享状态
 *     }
 * }
 * </pre>
 */
public interface AgentNode {

    /**
     * 节点名称唯一标识
     */
    String getName();

    /**
     * 执行节点逻辑
     *
     * @param state 当前执行状态，包含节点属性、消息历史等信息
     * @return 执行结果，包含更新后的状态和下一个节点名称
     */
    ExecutionResult execute(AgentState state);

    record ExecutionResult(AgentState state, String nextNode) {}
}
