package com.lambda.fusion.permission.service;

/**
 * 权限同步令牌校验器
 *
 * <p>服务端接收各服务上报的接口权限时，对上报方身份进行校验。
 * 默认实现基于静态配置的单一令牌（{@code lambda.fusion.permission.server.auth-token}）；
 * 部署方可提供自定义实现（例如按应用名查询服务表密钥）并通过 Spring 覆盖默认 Bean。
 */
public interface PermissionTokenVerifier {

    /**
     * 校验上报方令牌
     *
     * @param application 上报方应用名（spring.application.name），可能为 null
     * @param token       上报方携带的令牌
     * @throws SecurityException 校验失败时抛出
     */
    void verify(String application, String token);
}
