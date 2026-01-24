package com.lambda.fusion.authority.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lambda.fusion.authority.user.model.OnlineLogEntity;

public interface UserOnlineLogService extends IService<OnlineLogEntity> {

    Boolean isOnline(String username);

    void online(String username);

    void offline(String username);
}
