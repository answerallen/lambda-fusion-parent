package com.lambda.fusion.ai.apps.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lambda.fusion.ai.apps.model.CreateApp;
import com.lambda.fusion.ai.apps.model.Robot;
import com.lambda.fusion.ai.apps.model.UpdateApp;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import java.util.List;

public interface AppsService extends IService<AppEntity> {
    Robot createApp(CreateApp dto);

    Robot updateApp(UpdateApp dto);

    Robot getAppById(String id);

    List<Robot> listApps();

    void deleteRobot(String id);
}
