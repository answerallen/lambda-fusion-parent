package com.lambda.fusion.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.fusion.ai.commons.exception.AiBusinessException;
import com.lambda.fusion.ai.commons.exception.AiErrorCode;
import com.lambda.fusion.ai.mapper.RobotMapper;
import com.lambda.fusion.ai.model.CreateRobot;
import com.lambda.fusion.ai.model.Robot;
import com.lambda.fusion.ai.model.UpdateRobot;
import com.lambda.fusion.ai.model.entity.RobotEntity;
import com.lambda.fusion.ai.service.RobotService;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotServiceImpl extends ServiceImpl<RobotMapper, RobotEntity> implements RobotService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Robot createRobot(CreateRobot createRobot) {
        log.info("创建AI机器人: {}", createRobot.getName());
        RobotEntity entity = createRobot.toEntity();
        this.save(entity);
        log.info("AI机器人创建成功, id: {}", entity.getId());
        return ConvertUtils.convert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Robot updateRobot(UpdateRobot updateRobot) {
        log.info("更新AI机器人: {}", updateRobot.getId());

        if (updateRobot.getId() == null) {
            throw new AiBusinessException(AiErrorCode.ROBOT_NOT_FOUND, "机器人ID不能为空");
        }
        RobotEntity entity = updateRobot.toEntity();
        this.updateById(entity);
        log.info("AI机器人更新成功, id: {}", entity.getId());
        return ConvertUtils.convert(entity);
    }

    @Override
    public Robot getRobotById(String id) {
        if (id == null) {
            throw new AiBusinessException(AiErrorCode.ROBOT_NOT_FOUND, "机器人ID不能为空");
        }

        RobotEntity entity = this.getById(id);
        if (entity == null) {
            throw AiBusinessException.robotNotFound(id);
        }
        return ConvertUtils.convert(entity);
    }

    @Override
    public List<Robot> listAllRobots() {
        return this.list().stream()
                .map(ConvertUtils::<Robot, RobotEntity>convert)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRobot(String id) {
        if (id == null) {
            throw new AiBusinessException(AiErrorCode.ROBOT_NOT_FOUND, "机器人ID不能为空");
        }

        RobotEntity entity = this.getById(id);
        if (entity == null) {
            throw AiBusinessException.robotNotFound(id);
        }

        this.removeById(id);
        log.info("AI机器人删除成功, id: {}", id);
    }
}
