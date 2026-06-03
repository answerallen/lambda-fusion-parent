package com.lambda.fusion.authority.authentication.adapter;

import com.lambda.fusion.authority.api.RemoteAuthenticationService;
import com.lambda.security.service.UserDetailService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RemoteAuthenticationServiceAdapter implements RemoteAuthenticationService {
    private final UserDetailService userDetailService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return userDetailService.getPermissionList(loginId, loginType);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return userDetailService.getRoleList(loginId, loginType);
    }
}
