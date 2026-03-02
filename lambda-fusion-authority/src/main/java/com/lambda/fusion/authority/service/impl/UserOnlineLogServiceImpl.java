package com.lambda.fusion.authority.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.mapper.UserOnlineLogMapper;
import com.lambda.fusion.authority.model.user.OnlineLogEntity;
import com.lambda.fusion.authority.service.UserOnlineLogService;
import com.lambda.fusion.core.FusionConstants;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class UserOnlineLogServiceImpl extends ServiceImpl<UserOnlineLogMapper, OnlineLogEntity>
        implements UserOnlineLogService {

    @Override
    public Boolean isOnline(String username, String deviceType) {
        if (username == null) {
            throw AuthorityBusinessException.invalidParameter("username不能为空");
        }
        OnlineLogEntity entity = getOne(new LambdaQueryWrapper<OnlineLogEntity>()
                .eq(OnlineLogEntity::getUsername, username)
                .eq(
                        OnlineLogEntity::getDeviceType,
                        ObjectUtil.defaultIfBlank(deviceType, FusionConstants.DEVICE_DEFAULT))
                .orderByDesc(OnlineLogEntity::getOnlineTime)
                .last("LIMIT 1"));
        return entity != null && Integer.valueOf(1).equals(entity.getIsOnline());
    }

    @Override
    public void online(String username, String deviceType) {
        OnlineLogEntity entity = getOne(new LambdaQueryWrapper<OnlineLogEntity>()
                .eq(OnlineLogEntity::getUsername, username)
                .orderByDesc(OnlineLogEntity::getOnlineTime)
                .last("LIMIT 1"));
        if (entity == null) {
            entity = new OnlineLogEntity();
            entity.setUsername(username);
            entity.setDeviceType(ObjectUtil.defaultIfBlank(deviceType, FusionConstants.DEVICE_DEFAULT));
            entity.setIsOnline(FusionConstants.ENABLED);
            entity.setOnlineTime(LocalDateTime.now());
            save(entity);
        } else {
            entity.setIsOnline(FusionConstants.ENABLED);
            entity.setOnlineTime(LocalDateTime.now());
            update(
                    entity,
                    new LambdaQueryWrapper<OnlineLogEntity>()
                            .eq(OnlineLogEntity::getUsername, username)
                            .eq(OnlineLogEntity::getDeviceType, entity.getDeviceType()));
        }
    }

    @Override
    public void offline(String username, String deviceType) {
        OnlineLogEntity entity = new OnlineLogEntity();
        entity.setUsername(username);
        entity.setDeviceType(ObjectUtil.defaultIfBlank(deviceType, FusionConstants.DEVICE_DEFAULT));
        entity.setIsOnline(FusionConstants.DISABLED);
        entity.setOfflineTime(LocalDateTime.now());
        update(
                entity,
                new LambdaQueryWrapper<OnlineLogEntity>()
                        .eq(OnlineLogEntity::getUsername, username)
                        .eq(OnlineLogEntity::getDeviceType, entity.getDeviceType()));
    }
}
