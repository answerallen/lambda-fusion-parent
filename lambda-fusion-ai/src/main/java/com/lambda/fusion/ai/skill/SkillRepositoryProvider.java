package com.lambda.fusion.ai.skill;

import io.agentscope.core.skill.repository.AgentSkillRepository;

/**
 * 技能仓库源工厂 SPI：按 {@code type} 构造一个 {@link AgentSkillRepository}。
 *
 * <p>每个后端一个实现，{@code @ConditionalOnClass} 条件装配（扩展不在 classpath 时不注册）。
 * 委托给 AgentScope 已有仓库（MysqlSkillRepository/PostgresSkillRepository/GitSkillRepository/NacosSkillRepository）。
 *
 * @author Jin
 */
public interface SkillRepositoryProvider {

    /** 源类型标识：MYSQL / POSTGRES / GIT / NACOS。 */
    String type();

    /** 构造一个 {@link AgentSkillRepository}。 */
    AgentSkillRepository create();
}
