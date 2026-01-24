package com.lambda.fusion.authority.user.listenner;

import com.lambda.cloud.sse.listener.SseEventListener;
import com.lambda.fusion.authority.user.service.UserOnlineLogService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserSeeEventListener implements SseEventListener {

    private final UserOnlineLogService userOnlineLogService;

    @Override
    public void onDisconnect(String clientId) {
        if (clientId != null) {
            userOnlineLogService.online(clientId);
        }

    }

    @Override
    public void onConnect(String clientId) {
        if (clientId != null) {
            userOnlineLogService.offline(clientId);
        }
    }
}
