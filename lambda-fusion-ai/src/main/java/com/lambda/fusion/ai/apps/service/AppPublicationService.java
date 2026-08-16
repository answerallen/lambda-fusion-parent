package com.lambda.fusion.ai.apps.service;

import com.lambda.fusion.ai.apps.model.AppPublication;

/**
 * 应用发布服务。承载发布/下线/查询发布状态，独立于运行开关与受众授权（三态分离）。
 * 发布是有明确生命周期的独立能力，不并入通用 {@link AppService}。
 *
 * @author Jin
 */
public interface AppPublicationService {

    /**
     * 查询应用的发布状态与代码。
     *
     * @param appId 应用ID
     * @return 发布视图
     */
    AppPublication get(String appId);

    /**
     * 幂等发布：首次生成稳定 publishCode，已发布时直接返回现状（不改变代码）。
     *
     * @param appId 应用ID
     * @return 发布视图
     */
    AppPublication publish(String appId);

    /**
     * 幂等下线：仅关闭独立 URL，保留 publishCode，不删除应用、不关闭后台调试入口。
     *
     * @param appId 应用ID
     * @return 发布视图
     */
    AppPublication unpublish(String appId);
}
