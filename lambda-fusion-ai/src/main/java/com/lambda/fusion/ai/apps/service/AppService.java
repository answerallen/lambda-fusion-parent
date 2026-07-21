package com.lambda.fusion.ai.apps.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lambda.fusion.ai.apps.model.AppPageQuery;
import com.lambda.fusion.ai.apps.model.CreateApp;
import com.lambda.fusion.ai.apps.model.UpdateApp;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;

/**
 * @author Jin
 */
public interface AppService {

    Page<AppEntity> page(AppPageQuery query);

    AppEntity get(String id);

    AppEntity create(CreateApp dto);

    void update(String id, UpdateApp dto);

    void delete(String id);

    /**
     * 按主键加载（含禁用记录），限定当前租户；不存在抛出业务异常。供运行时使用。
     */
    AppEntity loadById(String id);
}
