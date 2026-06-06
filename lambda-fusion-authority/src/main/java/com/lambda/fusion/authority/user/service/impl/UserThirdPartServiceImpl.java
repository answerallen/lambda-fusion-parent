package com.lambda.fusion.authority.user.service.impl;

import com.lambda.fusion.authority.AuthorityConstants.ThirdType;
import com.lambda.fusion.authority.exception.AuthorityBusinessException;
import com.lambda.fusion.authority.user.mapper.UserThirdpartMapper;
import com.lambda.fusion.authority.user.model.ThirdPartBinding;
import com.lambda.fusion.authority.user.model.entity.UserThirdpartEntity;
import com.lambda.fusion.authority.user.service.UserThirdPartService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserThirdPartServiceImpl implements UserThirdPartService {

    private final UserThirdpartMapper userThirdpartMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bind(String username, String loginType, String openId) {
        UserThirdpartEntity existing = userThirdpartMapper.findByLoginTypeAndOpenId(loginType, openId);
        if (existing != null) {
            if (existing.getUsername().equals(username)) {
                throw AuthorityBusinessException.invalidParameter("该第三方账号已绑定当前用户");
            }
            throw AuthorityBusinessException.invalidParameter("该第三方账号已被其他用户绑定");
        }

        UserThirdpartEntity entity = new UserThirdpartEntity();
        entity.setUsername(username);
        entity.setLoginType(loginType);
        entity.setOpenId(openId);
        userThirdpartMapper.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(String loginType, String username) {
        int deleted = userThirdpartMapper.deleteByUsernameAndLoginType(username, loginType);
        if (deleted == 0) {
            throw AuthorityBusinessException.invalidParameter("未找到该第三方绑定信息");
        }
    }

    @Override
    public List<ThirdPartBinding> listByUsername(String username) {
        List<UserThirdpartEntity> entities = userThirdpartMapper.findByUsername(username);
        return entities.stream()
                .map(entity -> {
                    String label = resolveLabel(entity.getLoginType());
                    return new ThirdPartBinding(entity.getLoginType(), label, entity.getOpenId(), entity.getUsername());
                })
                .toList();
    }

    @Override
    public String findUsername(String loginType, String openId) {
        return userThirdpartMapper.findUsernameByLoginTypeAndOpenId(loginType, openId);
    }

    private String resolveLabel(String loginType) {
        try {
            return ThirdType.of(loginType).getLabel();
        } catch (IllegalArgumentException e) {
            return loginType;
        }
    }
}
