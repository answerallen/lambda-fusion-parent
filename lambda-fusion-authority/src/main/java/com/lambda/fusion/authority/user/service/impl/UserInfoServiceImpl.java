package com.lambda.fusion.authority.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lambda.cloud.core.principal.LoginUser;
import com.lambda.fusion.authority.AuthorityConstants.ThirdType;
import com.lambda.fusion.authority.user.mapper.UserInfoMapper;
import com.lambda.fusion.authority.user.model.entity.UserInfoEntity;
import com.lambda.fusion.authority.user.service.UserInfoService;
import com.lambda.fusion.authority.user.service.UserThirdPartService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfoEntity> implements UserInfoService {

    private final UserThirdPartService userThirdpartService;

    @Override
    public UserInfoEntity getProps(String id) {
        return baseMapper.getProps(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbindUserInfo(LoginUser loginUser, String type, String username) {
        userThirdpartService.unbind(type, username);
        clearUserInfoRedundantFields(type, username);
    }

    private void clearUserInfoRedundantFields(String type, String username) {
        try {
            ThirdType thirdType = ThirdType.of(type);
            LambdaUpdateWrapper<UserInfoEntity> wrapper =
                    new LambdaUpdateWrapper<UserInfoEntity>().eq(UserInfoEntity::getUsername, username);

            switch (thirdType) {
                case DING_TALK -> wrapper.set(UserInfoEntity::getDdNo, null).set(UserInfoEntity::getDdNick, null);
                case WX_MA, WX_OPEN ->
                    wrapper.set(UserInfoEntity::getWechatNo, null).set(UserInfoEntity::getWechatName, null);
                default -> {
                    return;
                }
            }
            baseMapper.update(null, wrapper);
        } catch (IllegalArgumentException ignored) {

        }
    }
}
