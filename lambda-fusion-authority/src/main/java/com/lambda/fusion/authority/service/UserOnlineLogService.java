package com.lambda.fusion.authority.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.authority.model.user.OnlineLogEntity;

public interface UserOnlineLogService extends IService<OnlineLogEntity> {

    Boolean isOnline(String username, String deviceType);

    void online(String username, String deviceType);

    void offline(String username, String deviceType);
}
