package com.lambda.fusion.ai.apps.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.ConvertUtils;
import com.lambda.fusion.ai.apps.mapper.AppsMapper;
import com.lambda.fusion.ai.apps.model.CreateApp;
import com.lambda.fusion.ai.apps.model.Robot;
import com.lambda.fusion.ai.apps.model.UpdateApp;
import com.lambda.fusion.ai.apps.model.entity.AppEntity;
import com.lambda.fusion.ai.apps.service.AppsService;
import com.lambda.fusion.ai.exception.AiBusinessException;
import com.lambda.fusion.ai.exception.AiErrorCode;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AppsServiceImpl extends ServiceImpl<AppsMapper, AppEntity> implements AppsService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Robot createApp(CreateApp createApp) {
        log.info("创建AI应用: {}", createApp.getName());
        AppEntity entity = createApp.toEntity();
        this.save(entity);
        log.info("AI应用创建成功, id: {}", entity.getId());
        return ConvertUtils.convert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Robot updateApp(UpdateApp updateApp) {
        log.info("更新AI应用: {}", updateApp.getId());

        if (updateApp.getId() == null) {
            throw new AiBusinessException(AiErrorCode.ROBOT_NOT_FOUND, "应用ID不能为空");
        }
        AppEntity entity = updateApp.toEntity();
        this.updateById(entity);
        log.info("AI应用更新成功, id: {}", entity.getId());
        return ConvertUtils.convert(entity);
    }

    @Override
    public Robot getAppById(String id) {
        if (id == null) {
            throw new AiBusinessException(AiErrorCode.ROBOT_NOT_FOUND, "应用ID不能为空");
        }

        AppEntity entity = this.getById(id);
        if (entity == null) {
            throw AiBusinessException.robotNotFound(id);
        }
        return ConvertUtils.convert(entity);
    }

    @Override
    public List<Robot> listApps() {
        return this.list().stream().map(ConvertUtils::<Robot, AppEntity>convert).collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRobot(String id) {
        if (id == null) {
            throw new AiBusinessException(AiErrorCode.ROBOT_NOT_FOUND, "应用ID不能为空");
        }

        AppEntity entity = this.getById(id);
        if (entity == null) {
            throw AiBusinessException.robotNotFound(id);
        }

        this.removeById(id);
        log.info("AI应用删除成功, id: {}", id);
    }
}
