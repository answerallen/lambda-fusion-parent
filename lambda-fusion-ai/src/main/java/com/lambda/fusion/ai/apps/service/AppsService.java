package com.lambda.fusion.ai.apps.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lambda.fusion.ai.apps.model.App;
import com.lambda.fusion.ai.apps.model.CreateApp;
import com.lambda.fusion.ai.apps.model.UpdateApp;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import java.util.List;

public interface AppsService extends IService<AppEntity> {
    App createApp(CreateApp dto);

    App updateApp(UpdateApp dto);

    App getAppById(String id);

    List<App> listApps();

    void deleteApp(String id);
}
