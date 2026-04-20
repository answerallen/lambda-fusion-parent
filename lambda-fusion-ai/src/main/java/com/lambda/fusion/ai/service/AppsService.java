package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.model.CreateApp;
import com.lambda.fusion.ai.model.Robot;
import com.lambda.fusion.ai.model.UpdateApp;
import com.lambda.fusion.ai.model.entity.AppEntity;
import java.util.List;

public interface AppsService extends IService<AppEntity> {
    Robot createApp(CreateApp dto);

    Robot updateApp(UpdateApp dto);

    Robot getAppById(String id);

    List<Robot> listApps();

    void deleteRobot(String id);
}
