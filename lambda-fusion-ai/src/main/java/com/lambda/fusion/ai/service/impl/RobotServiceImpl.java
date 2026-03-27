package com.lambda.fusion.ai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.ai.mapper.RobotMapper;
import com.lambda.fusion.ai.model.CreateRobot;
import com.lambda.fusion.ai.model.Robot;
import com.lambda.fusion.ai.model.UpdateRobot;
import com.lambda.fusion.ai.model.entity.RobotEntity;
import com.lambda.fusion.ai.service.RobotService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RobotServiceImpl extends ServiceImpl<RobotMapper, RobotEntity> implements RobotService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Robot createRobot(CreateRobot dto) {
        RobotEntity entity = new RobotEntity();
        BeanUtils.copyProperties(dto, entity);
        entity.setRobotId(UUID.randomUUID().toString().replace("-", ""));
        this.save(entity);
        return convertToDto(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Robot updateRobot(UpdateRobot dto) {
        RobotEntity entity = this.getById(dto.getId());
        if (entity == null) {
            throw new RuntimeException("Robot not found");
        }
        BeanUtils.copyProperties(dto, entity, "id", "robotId");
        this.updateById(entity);
        return convertToDto(entity);
    }

    @Override
    public Robot getRobotById(Long id) {
        RobotEntity entity = this.getById(id);
        if (entity == null) {
            return null;
        }
        return convertToDto(entity);
    }

    @Override
    public List<Robot> listAllRobots() {
        return this.list().stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRobot(Long id) {
        this.removeById(id);
    }

    private Robot convertToDto(RobotEntity entity) {
        Robot dto = new Robot();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
