package com.lambda.fusion.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.ai.model.CreateRobot;
import com.lambda.fusion.ai.model.Robot;
import com.lambda.fusion.ai.model.UpdateRobot;
import com.lambda.fusion.ai.model.entity.RobotEntity;
import java.util.List;

public interface RobotService extends IService<RobotEntity> {
    Robot createRobot(CreateRobot dto);

    Robot updateRobot(UpdateRobot dto);

    Robot getRobotById(Long id);

    List<Robot> listAllRobots();

    void deleteRobot(Long id);
}
