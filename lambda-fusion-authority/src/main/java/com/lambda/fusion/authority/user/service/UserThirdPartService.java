package com.lambda.fusion.authority.user.service;

import com.lambda.fusion.authority.user.model.ThirdPartBinding;
import java.util.List;

public interface UserThirdPartService {

    void bind(String username, String loginType, String openId);

    void unbind(String loginType, String username);

    List<ThirdPartBinding> listByUsername(String username);

    String findUsername(String loginType, String openId);
}
