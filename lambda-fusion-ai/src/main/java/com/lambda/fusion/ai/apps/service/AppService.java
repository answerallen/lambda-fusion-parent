package com.lambda.fusion.ai.apps.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.apps.model.AppPageQuery;
import com.lambda.fusion.ai.apps.model.CreateApp;
import com.lambda.fusion.ai.apps.model.UpdateApp;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import java.util.List;

public interface AppService {

    Page<AppEntity> page(AppPageQuery query);

    AppEntity get(String id);

    AppEntity create(CreateApp dto);

    void update(String id, UpdateApp dto);

    void delete(String id);

    /**
     * 按主键加载（含禁用记录），全局；不存在抛出业务异常。供运行时使用。
     */
    AppEntity loadById(String id);

    /**
     * 列出当前用户可见的应用（平台应用按 audience+角色；独立应用按 owner）。
     *
     * @return 可见应用列表
     */
    List<AppEntity> listAvailable();

    /**
     * 加载并校验当前用户对应用可见；不可见或不存在抛 APP_NOT_FOUND。供建会话等校验。
     *
     * @param appId 应用ID
     * @return 应用实体
     */
    AppEntity loadAvailable(String appId);
}
