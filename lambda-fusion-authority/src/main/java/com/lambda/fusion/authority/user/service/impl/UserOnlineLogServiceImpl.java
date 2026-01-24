package com.lambda.fusion.authority.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.utils.Assert;
import com.lambda.fusion.authority.user.mapper.UserOnlineLogMapper;
import com.lambda.fusion.authority.user.model.OnlineLogEntity;
import com.lambda.fusion.authority.user.service.UserOnlineLogService;
import com.lambda.fusion.core.FusionConstants;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserOnlineLogServiceImpl extends ServiceImpl<UserOnlineLogMapper, OnlineLogEntity>
        implements UserOnlineLogService {

    @Override
    public Boolean isOnline(String username) {
        Assert.notNull(username, "username not empty");
        OnlineLogEntity entity = getOne(new LambdaQueryWrapper<OnlineLogEntity>()
                .eq(OnlineLogEntity::getUsername, username)
                .orderByDesc(OnlineLogEntity::getOnlineTime)
                .last("LIMIT 1"));
        return entity != null && Integer.valueOf(1).equals(entity.getIsOnline());
    }

    @Override
    public void online(String username) {
        OnlineLogEntity entity = new OnlineLogEntity();
        entity.setUsername(username);
        entity.setType(0);
        entity.setIsOnline(FusionConstants.ENABLED);
        entity.setOnlineTime(LocalDateTime.now());
        save(entity);
    }

    @Override
    public void offline(String username) {
        OnlineLogEntity entity = new OnlineLogEntity();
        entity.setUsername(username);
        entity.setType(0);
        entity.setIsOnline(FusionConstants.DISABLED);
        entity.setOnlineTime(LocalDateTime.now());
        save(entity);
    }
}
