package com.lambda.fusion.authority.user.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lambda.fusion.authority.user.model.entity.OnlineLogEntity;

public interface UserOnlineLogService extends IService<OnlineLogEntity> {

    Boolean isOnline(String username, String deviceType);

    void online(String username, String deviceType);

    void offline(String username, String deviceType);
}
